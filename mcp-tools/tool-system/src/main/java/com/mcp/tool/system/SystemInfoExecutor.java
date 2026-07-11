package com.mcp.tool.system;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.lang.management.*;
import java.util.*;

/**
 * 系统信息 MCP 工具
 *
 * 提供服务器运行状态查询能力，包括：
 * - JVM 内存/线程/GC 信息
 * - 系统 CPU/负载/磁盘信息
 * - 运行时间/版本
 */
@Component
@ConditionalOnProperty(name = "mcp.tool.system.enabled", havingValue = "true", matchIfMissing = true)
public class SystemInfoExecutor implements McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(SystemInfoExecutor.class);

    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition def = new ToolDefinition();
        def.setName("system_info");
        def.setDisplayName("系统信息");
        def.setDescription("获取 MCP Server 运行状态，包括 JVM 内存、CPU、线程、GC 等系统指标。");
        def.setCategory("system");
        def.setVersion("1.0.0");
        def.setModule("tool-system");
        def.setRequiredRoles("admin");
        def.setTimeoutMs(5000);
        def.setRateLimitPerSecond(10);

        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", Map.of(
                "type", "string",
                "description", "查询类型：basic（基本信息）/ memory（内存详情）/ gc（GC 统计）/ all（全部）",
                "enum", List.of("basic", "memory", "gc", "all"),
                "default", "basic"
        ));

        inputSchema.put("properties", properties);
        def.setInputSchema(inputSchema);

        return def;
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String type = params.containsKey("type") ? params.get("type").toString() : "basic";
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("tool", "system_info");

            switch (type) {
                case "memory":
                    result.put("memory", collectMemoryInfo());
                    break;
                case "gc":
                    result.put("gc", collectGcInfo());
                    break;
                case "all":
                    result.put("jvm", collectJvmInfo());
                    result.put("memory", collectMemoryInfo());
                    result.put("threads", collectThreadInfo());
                    result.put("gc", collectGcInfo());
                    result.put("os", collectOsInfo());
                    break;
                default:
                    result.put("jvm", collectJvmInfo());
                    result.put("memory", Map.of(
                            "heapUsed", formatBytes(getHeapMemoryUsage().getUsed()),
                            "heapMax", formatBytes(getHeapMemoryUsage().getMax()),
                            "heapUsagePercent", String.format("%.1f%%",
                                    (double) getHeapMemoryUsage().getUsed() / getHeapMemoryUsage().getMax() * 100)
                    ));
                    result.put("os", collectOsInfo());
                    break;
            }

            result.put("elapsedMs", System.currentTimeMillis() - startTime);
            return Mono.just(result);
        } catch (Exception e) {
            log.warn("系统信息获取失败: {}", e.getMessage());
            return Mono.just(Map.of(
                    "success", false,
                    "error", "系统信息获取失败: " + e.getMessage(),
                    "tool", "system_info"
            ));
        }
    }

    // ===== JVM 信息 =====

    private Map<String, Object> collectJvmInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", runtime.getVmName());
        info.put("vendor", runtime.getVmVendor());
        info.put("version", runtime.getVmVersion());
        info.put("uptime", formatUptime(runtime.getUptime()));
        info.put("startTime", new Date(runtime.getStartTime()).toString());
        info.put("inputArguments", runtime.getInputArguments());
        return info;
    }

    // ===== 内存信息 =====

    private MemoryUsage getHeapMemoryUsage() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    }

    private Map<String, Object> collectMemoryInfo() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("heap", Map.of(
                "init", formatBytes(heap.getInit()),
                "used", formatBytes(heap.getUsed()),
                "committed", formatBytes(heap.getCommitted()),
                "max", formatBytes(heap.getMax()),
                "usagePercent", String.format("%.1f%%", (double) heap.getUsed() / heap.getMax() * 100)
        ));
        info.put("nonHeap", Map.of(
                "init", formatBytes(nonHeap.getInit()),
                "used", formatBytes(nonHeap.getUsed()),
                "committed", formatBytes(nonHeap.getCommitted()),
                "max", formatBytes(nonHeap.getMax())
        ));

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        List<Map<String, Object>> poolList = new ArrayList<>();
        for (MemoryPoolMXBean pool : pools) {
            MemoryUsage usage = pool.getUsage();
            if (usage != null) {
                poolList.add(Map.of(
                        "name", pool.getName(),
                        "type", pool.getType().toString(),
                        "used", formatBytes(usage.getUsed()),
                        "max", formatBytes(usage.getMax()),
                        "usagePercent", String.format("%.1f%%", (double) usage.getUsed() / usage.getMax() * 100)
                ));
            }
        }
        info.put("pools", poolList);

        return info;
    }

    // ===== 线程信息 =====

    private Map<String, Object> collectThreadInfo() {
        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("activeThreads", thread.getThreadCount());
        info.put("peakThreads", thread.getPeakThreadCount());
        info.put("daemonThreads", thread.getDaemonThreadCount());
        info.put("totalStarted", thread.getTotalStartedThreadCount());
        return info;
    }

    // ===== GC 信息 =====

    private Map<String, Object> collectGcInfo() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        List<Map<String, Object>> gcList = new ArrayList<>();
        for (GarbageCollectorMXBean gc : gcBeans) {
            gcList.add(Map.of(
                    "name", gc.getName(),
                    "collectionCount", gc.getCollectionCount(),
                    "collectionTime", gc.getCollectionTime() + "ms",
                    "memoryPoolNames", List.of(gc.getMemoryPoolNames())
            ));
        }
        return Map.of("collectors", gcList);
    }

    // ===== OS 信息 =====

    private Map<String, Object> collectOsInfo() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", os.getName());
        info.put("arch", os.getArch());
        info.put("version", os.getVersion());
        info.put("availableProcessors", os.getAvailableProcessors());
        info.put("systemLoadAverage", os.getSystemLoadAverage());

        // 尝试获取更详细的 OS 信息（JDK 17 支持）
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            info.put("totalMemorySize", formatBytes(sunOs.getTotalMemorySize()));
            info.put("freeMemorySize", formatBytes(sunOs.getFreeMemorySize()));
            info.put("totalSwapSpaceSize", formatBytes(sunOs.getTotalSwapSpaceSize()));
            info.put("freeSwapSpaceSize", formatBytes(sunOs.getFreeSwapSpaceSize()));
            try {
                info.put("cpuLoad", String.format("%.2f%%", sunOs.getCpuLoad() * 100));
                info.put("processCpuLoad", String.format("%.2f%%", sunOs.getProcessCpuLoad() * 100));
            } catch (Exception ignored) {
            }
        }

        return info;
    }

    // ===== 工具方法 =====

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatUptime(long uptimeMs) {
        long days = uptimeMs / (24 * 60 * 60 * 1000);
        long hours = (uptimeMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (uptimeMs % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (uptimeMs % (60 * 1000)) / 1000;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天 ");
        if (hours > 0) sb.append(hours).append("小时 ");
        sb.append(minutes).append("分 ").append(seconds).append("秒");
        return sb.toString();
    }
}
