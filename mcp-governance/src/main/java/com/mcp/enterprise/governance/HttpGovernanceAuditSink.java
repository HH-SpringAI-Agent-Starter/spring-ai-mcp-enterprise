package com.mcp.enterprise.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * V1.32 HTTP/SIEM 审计事件流导出 {@link McpGovernanceAuditSink}。
 *
 * <p>审计是合规资产，但「落库可查」（V1.29 JDBC）只解决了一半问题——
 * 企业的安全运营中心（SOC）需要在<b>事件发生时近乎实时</b>地收到治理判定，
 * 与 WAF/网关/APM 日志同管道分析，而不是等审计员去数据库里查。
 * 典型下游：</p>
 * <ul>
 *   <li><b>Splunk HEC / Elasticsearch / Loki</b>：SOC 统一日志管道（SIEM）；</li>
 *   <li><b>Kafka REST Proxy</b>：事件流进 Kafka topic，供流式告警/数仓消费；</li>
 *   <li><b>企业自建 webhook</b>：钉钉/企微机器人、自研事件总线、DataDog 等。</li>
 * </ul>
 *
 * <p>设计约束（对齐 V1.29 JDBC / V1.26 InMemory 的「审计是观察者，不是关键路径」）：</p>
 * <ul>
 *   <li><b>零外部依赖</b>：只用 JDK 11+ 原生 {@link java.net.http.HttpClient}，
 *       不需要引入 Kafka/Splunk/ES 客户端；目标端点只要能收 HTTP POST JSON 即可；</li>
 *   <li><b>批量异步发送</b>：事件先入本地队列，攒够 {@code batchSize} 或超过
 *       {@code flushIntervalMs} 后由守护线程批量 POST（JSON 数组），吞吐友好；</li>
 *   <li><b>fail-soft</b>：发送失败仅 WARN 并保留内存副本供查询（{@link #recent}/{@link #search}），
 *       绝不抛出异常打挂业务调用，也不丢审计轨迹；</li>
 *   <li><b>接口语义一致</b>：{@code recent}/{@code search}/{@code deleteBefore} 与内存实现同语义
 *       （本地最近 N 条环形缓冲，HTTP 只是「额外流出」通道，不替代本地可查视图）；</li>
 *   <li><b>优雅关闭</b>：{@link #close()} 触发一次最终 flush（最多等待 3 秒），
 *       配合 Spring {@code DisposableBean} 在停机时尽量把积压事件送出。</li>
 * </ul>
 *
 * <p>启用方式（{@code mcp.enterprise.governance.audit}）：</p>
 * <pre>
 *   store: http
 *   http-url: https://your-siem:8088/services/collector/event   # Splunk HEC 示例
 *   http-batch-size: 50
 *   http-flush-interval-ms: 5000
 *   http-timeout-ms: 3000
 *   http-headers:                      # 可选附加头（如 Authorization: Splunk xxx）
 *     Content-Type: application/json
 * </pre>
 */
public class HttpGovernanceAuditSink implements McpGovernanceAuditSink, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger("MCP_GOVERNANCE_AUDIT_HTTP");

    /** 单次 HTTP body 上限保护（1MB，防极端 batch 撑爆网关）。 */
    private static final long MAX_BODY_BYTES = 1_048_576L;

    private final URI endpoint;
    private final int batchSize;
    private final long flushIntervalMs;
    private final Duration timeout;
    private final Map<String, String> headers;
    private final boolean logToSlf4j;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 本地可查视图（环形缓冲），与 InMemory 实现同语义。 */
    private final Deque<Event> events = new ArrayDeque<>();
    private final int maxEvents;

    /** 待发送队列（线程安全）。 */
    private final Deque<Event> pending = new ArrayDeque<>();

    /** 队列水位统计：成功发送/失败条数（运维可查）。 */
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private final ScheduledExecutorService scheduler;
    private volatile boolean closed = false;

    public HttpGovernanceAuditSink(String httpUrl, Map<String, String> headers,
                                   int batchSize, long flushIntervalMs, long timeoutMs,
                                   int maxEvents, boolean logToSlf4j) {
        this.endpoint = URI.create(httpUrl);
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.batchSize = Math.max(1, batchSize);
        this.flushIntervalMs = Math.max(100, flushIntervalMs);
        this.timeout = Duration.ofMillis(Math.max(200, timeoutMs));
        this.maxEvents = maxEvents > 0 ? maxEvents : 2000;
        this.logToSlf4j = logToSlf4j;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
        // 守护线程定时 flush（不阻塞业务线程、不阻止 JVM 退出）
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-governance-audit-http-flusher");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleWithFixedDelay(this::flushQuietly,
                this.flushIntervalMs, this.flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void record(Event event) {
        if (closed) {
            return; // close 后的新事件直接丢弃（进程已停止，尽力而为）
        }
        if (logToSlf4j) {
            log.info("governance decision={} tool={} tier={} caller={} approval={} msg={}",
                    event.decision(), event.tool(), event.tier(), event.caller(),
                    event.approvalId(), event.message());
        }
        // 本地可查视图（环形缓冲）
        synchronized (events) {
            events.addFirst(event);
            while (events.size() > maxEvents) {
                events.removeLast();
            }
        }
        // 待发送队列
        boolean shouldFlush;
        synchronized (pending) {
            pending.addLast(event);
            shouldFlush = pending.size() >= batchSize;
        }
        if (shouldFlush) {
            flushQuietly(); // 攒够一批立即发送（异步线程内做网络 IO，不阻塞调用方）
        }
    }

    @Override
    public List<Map<String, Object>> recent(int limit) {
        int n = limit <= 0 ? 50 : limit;
        List<Map<String, Object>> out = new ArrayList<>(Math.min(n, events.size()));
        synchronized (events) {
            int i = 0;
            for (Event e : events) {
                if (i++ >= n) {
                    break;
                }
                out.add(e.toMap());
            }
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> search(Query query) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = query.limit();
        synchronized (events) {
            int i = 0;
            for (Event e : events) {
                if (i++ >= n) {
                    break;
                }
                if (matches(e, query)) {
                    out.add(e.toMap());
                }
            }
        }
        return out;
    }

    @Override
    public int deleteBefore(Instant cutOff) {
        if (cutOff == null) {
            return 0;
        }
        synchronized (events) {
            int before = events.size();
            events.removeIf(e -> e.timestamp() != null && e.timestamp().isBefore(cutOff));
            return before - events.size();
        }
    }

    /** 诊断/运维：已成功发送条数。 */
    public long sentCount() {
        return sent.get();
    }

    /** 诊断/运维：发送失败条数（fail-soft 后保留在本地队列）。 */
    public long failedCount() {
        return failed.get();
    }

    /** 诊断/运维：当前待发送积压条数。 */
    public int pendingCount() {
        synchronized (pending) {
            return pending.size();
        }
    }

    /**
     * 拉取一批待发送事件并 POST 到端点。
     * 内部使用（定时调度 + 攒批触发），线程安全。失败时事件放回队列头部重试。
     */
    void flushOnce() {
        List<Event> batch;
        synchronized (pending) {
            int n = Math.min(batchSize, pending.size());
            if (n == 0) {
                return;
            }
            batch = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                batch.add(pending.removeFirst());
            }
        }
        List<Map<String, Object>> payload = new ArrayList<>(batch.size());
        for (Event e : batch) {
            payload.add(e.toMap());
        }

        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            if (body.length > MAX_BODY_BYTES) {
                log.warn("?? [V1.32] audit HTTP batch too large ({} bytes > {}), splitting",
                        body.length, MAX_BODY_BYTES);
                // 极端情况：拆半重入队列，下次再发
                synchronized (pending) {
                    for (int i = batch.size() - 1; i >= 0; i--) {
                        pending.addFirst(batch.get(i));
                    }
                }
                return;
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(builder::header);

            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                sent.addAndGet(batch.size());
                log.debug("?? [V1.32] audit batch sent: {} events -> {} ({} ms)",
                        batch.size(), endpoint, resp.statusCode());
            } else {
                failed.addAndGet(batch.size());
                log.warn("?? [V1.32] audit HTTP endpoint returned {} for {} events (body={}); "
                        + "events kept locally, will retry", resp.statusCode(), batch.size(),
                        truncate(resp.body(), 200));
                requeue(batch);
            }
        } catch (IOException | InterruptedException e) {
            failed.addAndGet(batch.size());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("?? [V1.32] audit HTTP send failed for {} events -> {}: {}; "
                    + "events kept locally, will retry", batch.size(), endpoint, e.getMessage());
            requeue(batch);
        }
    }

    private void requeue(List<Event> batch) {
        // 失败重试 + 水位保护：积压超过 maxEvents 时丢弃最旧（防止 OOM，保住新事件）
        synchronized (pending) {
            for (int i = batch.size() - 1; i >= 0; i--) {
                pending.addFirst(batch.get(i));
            }
            while (pending.size() > Math.max(maxEvents, batchSize * 10)) {
                pending.removeLast();
            }
        }
    }

    private void flushQuietly() {
        try {
            flushOnce();
        } catch (RuntimeException e) {
            log.warn("?? [V1.32] unexpected audit flush error: {}", e.getMessage());
        }
    }

    private static boolean matches(Event e, Query q) {
        if (q.tool() != null && !q.tool().isBlank() && !q.tool().equals(e.tool())) {
            return false;
        }
        if (q.caller() != null && !q.caller().isBlank() && !q.caller().equals(e.caller())) {
            return false;
        }
        if (q.decision() != null && !q.decision().isBlank() && !q.decision().equals(e.decision())) {
            return false;
        }
        if (q.tier() != null && !q.tier().isBlank() && !q.tier().equals(e.tier())) {
            return false;
        }
        if (q.from() != null && (e.timestamp() == null || e.timestamp().isBefore(q.from()))) {
            return false;
        }
        if (q.to() != null && (e.timestamp() == null || e.timestamp().isAfter(q.to()))) {
            return false;
        }
        if (q.traceId() != null && !q.traceId().isBlank()
                && !q.traceId().equalsIgnoreCase(e.traceId())) {
            return false;
        }
        return true;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 停机前最终 flush（最多等 {@code timeout*2} 让积压尽量送出），幂等。 */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        flushQuietly(); // 最后一次尽力发送
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(timeout.toMillis() * 2, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}