package com.mcp.enterprise.example.monitor;

import com.mcp.enterprise.monitor.McpAlertService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 应用内安全告警规则（参考实现 · V1.65 · 2026-11-07）。
 *
 * 定位：作为 examples/monitor-alert-rules/mcp-security-alerts.yml（Prometheus 层）的补充，
 * 负责「进程内快速反应」。McpAlertService 自带 5 分钟同类抑制（suppressMs = 300_000），
 * 适合对凭据盗刷 / 工具爆破 / 拒绝风暴做即时降级，不必等 Prometheus 抓取周期。
 *
 * ⚠️ 边界（务必看 README.md）：
 *   AlertRule 的条件只吃 McpMetricsCollector.ToolMetrics（错误率/延迟/调用数），
 *   表达不了「出站白名单被拒」「子进程派生」「凭据访问」「令牌受众不匹配」。
 *   这四类必须走 mcp_security_* 埋点 + Prometheus 规则，不能硬塞进这里。
 *
 * 接入：把本类复制为自己的 @Configuration 并注入 McpAlertService
 *      （mcp-monitor 的 McpMonitorAutoConfiguration 已提供该 Bean）。
 *      本文件位于 examples/ 下，不在 Maven reactor 中，不影响主构建。
 */
@Configuration
public class McpSecurityAlertRules {

    private static final Logger log = LoggerFactory.getLogger(McpSecurityAlertRules.class);

    private final McpAlertService alertService;

    public McpSecurityAlertRules(McpAlertService alertService) {
        this.alertService = alertService;
    }

    @PostConstruct
    public void install() {
        for (McpAlertService.AlertRule rule : securityRules()) {
            alertService.addRule(rule);
        }
        alertService.registerCallback(this::forwardToSiem);
        log.info("🛡️ MCP 安全告警规则已装载：{} 条 + SIEM 回调", securityRules().size());
    }

    /**
     * 六条规则全部只依赖 ToolMetrics 已有字段，不需要新增指标埋点。
     * 设计原则：「成功但异常」与「失败密集」都要能报，因为攻击往往不产生错误率。
     */
    private List<McpAlertService.AlertRule> securityRules() {
        return List.of(
                // ① 凭据盗刷 / 工具面爆破：短窗口内大量调用且绝大多数失败
                new McpAlertService.AlertRule(
                        "credential_stuffing_burst",
                        "疑似凭据盗刷：高频调用且失败率 >80%",
                        "CRITICAL",
                        m -> m.totalInvocations() >= 30 && m.errorRate() > 0.80),

                // ② 突发探测：首次与末次调用间隔极短但调用量很大
                //    （ToolMetrics 无历史窗口，用 first/lastInvocationTime 近似「爆发度」）
                new McpAlertService.AlertRule(
                        "tool_probe_burst",
                        "疑似工具枚举 / 批量探测：短时间窗内密集调用",
                        "CRITICAL",
                        m -> m.totalInvocations() >= 50
                                && (m.lastInvocationTime() - m.firstInvocationTime()) < 10_000L),

                // ③ 拒绝风暴：治理层拦截了大量请求，服务错误率却可能仍为 0
                new McpAlertService.AlertRule(
                        "rejection_storm",
                        "治理层拒绝风暴（错误率 >60% 且调用量 >=20）",
                        "WARNING",
                        m -> m.totalInvocations() >= 20 && m.errorRate() > 0.60),

                // ④ 慢速外传：调用全部成功、延迟异常高（大批量读取 / 外传特征）
                //    常规「错误率告警」对此完全沉默
                new McpAlertService.AlertRule(
                        "slow_exfiltration",
                        "疑似大批量读取：零错误但平均延迟 >8s",
                        "WARNING",
                        m -> m.avgLatencyMs() > 8_000
                                && m.errorRate() < 0.05
                                && m.totalInvocations() >= 5),

                // ⑤ 静默高吞吐：矿机 / 自动化滥用常表现为「零错误 + 稳定高频」
                //    与 Prometheus 侧 McpSilentHighThroughput 互为双保险
                new McpAlertService.AlertRule(
                        "silent_high_throughput",
                        "零错误持续高吞吐（需人工甄别自动化滥用）",
                        "INFO",
                        m -> m.totalInvocations() >= 200 && m.errorRate() == 0.0),

                // ⑥ 工具被反复重试打挂：错误率中等但延迟抬升，先于服务不可用出现
                new McpAlertService.AlertRule(
                        "degrading_before_outage",
                        "宕机前兆：错误率 >30% 且延迟 >3s",
                        "WARNING",
                        m -> m.totalInvocations() >= 8
                                && m.errorRate() > 0.30
                                && m.avgLatencyMs() > 3_000)
        );
    }

    /**
     * 告警 → SIEM / 审计流。
     *
     * ⚠️ 审计优先于告警：告警可能丢失或被抑制，但 append-only 审计不能丢。
     * 生产环境应把 event.toMap() 写入 JdbcGovernanceAuditSink / SIEM，
     * 而不是只打日志 —— 这是广发 13322 号公告「服务行为自动留痕、日志不可篡改」的落地方式。
     */
    private void forwardToSiem(McpAlertService.AlertEvent event) {
        Map<String, Object> payload = event.toMap();
        log.warn("🚨 SECURITY_ALERT rule={} severity={} tool={} payload={}",
                event.ruleId(), event.severity(), event.toolName(), payload);

        // 示例：接入方在此替换为真实 SIEM 推送
        // siemClient.push("mcp.security.alert", payload);
        // auditSink.append(payload);   // append-only，禁止 update/delete
    }
}
