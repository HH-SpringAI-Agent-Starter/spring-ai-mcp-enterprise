package com.mcp.tool.finance;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金融合规日历工具 (企业场景模板 - 金融 · 二期)
 *
 * 提供投研/金融机构高频合规场景支撑：
 * - 财报披露窗口（A股：年报/一季报/中报/三季报 法定期限）
 * - 关键监管节点（业绩预告 / 快报 / 股东大会召集）
 * - 指定月份合规日历一键生成，供智能体直接消费
 *
 * 面向场景：合规日历助手、投研 Agent 的披露窗口提醒、监管节点清单。
 */
@Component
public class FinanceComplianceExecutor implements McpToolExecutor {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of(
                "type", "string",
                "description", "操作类型: calendar(生成指定月份合规日历) | deadline(查询指定报告期披露截止日)"
        ));
        properties.put("year", Map.of(
                "type", "integer",
                "description", "年份，如 2026"
        ));
        properties.put("month", Map.of(
                "type", "integer",
                "description", "月份(1-12)，calendar 操作必填"
        ));
        properties.put("period", Map.of(
                "type", "string",
                "description", "报告期: annual(年报) | q1(一季报) | interim(中报) | q3(三季报)，deadline 操作必填"
        ));

        return new ToolDefinition(
                "finance_compliance", "合规日历",
                "生成指定月份 A 股财报披露窗口与监管节点合规日历，或查询报告期披露截止日，面向金融合规/投研智能体场景",
                "finance", "1.0.0", null, true, "admin,user", 5000, 20,
                Map.of("type", "object", "properties", properties,
                        "required", List.of("action")), null
        );
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String action = params != null && params.get("action") != null
                ? String.valueOf(params.get("action")).toLowerCase() : "";

        try {
            return switch (action) {
                case "calendar" -> Mono.just(buildCalendar(params));
                case "deadline" -> Mono.just(buildDeadline(params));
                default -> Mono.just(Map.of("success", false, "error",
                        "不支持的 action: " + action + "，可选 calendar/deadline"));
            };
        } catch (Exception e) {
            return Mono.just(Map.of("success", false, "error", "执行异常: " + e.getMessage()));
        }
    }

    private int intParam(Map<String, Object> p, String key, int defaultValue) {
        Object v = p != null ? p.get(key) : null;
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + key + " 不是有效整数: " + v);
        }
    }

    private Map<String, Object> buildCalendar(Map<String, Object> params) {
        int year = intParam(params, "year", LocalDate.now().getYear());
        int month = intParam(params, "month", LocalDate.now().getMonthValue());
        if (month < 1 || month > 12) throw new IllegalArgumentException("month 必须在 1-12 之间");
        YearMonth ym = YearMonth.of(year, month);

        // A股定期报告法定期限（中国证监会规定）：每个事件自带月份，按请求月份过滤
        List<Map<String, Object>> events = List.of(
                ev(1, 31, "年报业绩预告截止（净利润为负/扭亏/±50%变动等强制情形）", "年报预告"),
                ev(2, 28, "科创板/创业板年报业绩快报披露窗口（预计净利±50%情形）", "业绩快报"),
                ev(3, 31, "年报披露窗口开启：3-4月为年报密集披露期", "年报窗口"),
                ev(4, 30, "年报 + 一季报法定披露截止日（4月30日前）", "年报/一季报"),
                ev(5, 1, "股东大会年度会议召集与召开旺季（5-6月）", "股东大会"),
                ev(7, 15, "中报业绩预告截止（强制预告情形）", "中报预告"),
                ev(8, 31, "中报法定披露截止日（8月31日前）", "中报"),
                ev(10, 15, "三季报业绩预告截止（强制预告情形）", "三季报预告"),
                ev(10, 31, "三季报法定披露截止日（10月31日前）", "三季报")
        );

        List<Map<String, Object>> monthEvents = events.stream()
                .filter(e -> YearMonth.parse(String.valueOf(e.get("month")), MONTH_FMT).equals(ym))
                .toList();

        return Map.of(
                "success", true,
                "action", "calendar",
                "month", ym.format(MONTH_FMT),
                "eventCount", monthEvents.size(),
                "events", monthEvents,
                "note", "以上为 A 股定期报告法定期限节点，具体以交易所最新规则为准"
        );
    }

    private Map<String, Object> ev(int month, int day, String desc, String type) {
        // 事件固定为每年重复：month 为事件所在月份，day 为事件日期
        int year = LocalDate.now().getYear();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", String.format("%04d-%02d", year, month));
        m.put("date", String.format("%04d-%02d-%02d", year, month, day));
        m.put("type", type);
        m.put("description", desc);
        return m;
    }

    private Map<String, Object> buildDeadline(Map<String, Object> params) {
        int year = intParam(params, "year", LocalDate.now().getYear());
        String period = params != null && params.get("period") != null
                ? String.valueOf(params.get("period")).toLowerCase() : "";

        LocalDate deadline;
        String reportName;
        switch (period) {
            case "annual" -> { deadline = LocalDate.of(year, 4, 30); reportName = "年报"; }
            case "q1" -> { deadline = LocalDate.of(year, 4, 30); reportName = "一季报"; }
            case "interim" -> { deadline = LocalDate.of(year, 8, 31); reportName = "中报"; }
            case "q3" -> { deadline = LocalDate.of(year, 10, 31); reportName = "三季报"; }
            default -> throw new IllegalArgumentException(
                    "不支持的 period: " + period + "，可选 annual/q1/interim/q3");
        }

        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        return Map.of(
                "success", true,
                "action", "deadline",
                "reportPeriod", period,
                "reportName", reportName,
                "fiscalYear", year,
                "disclosureDeadline", deadline.toString(),
                "daysRemaining", daysLeft >= 0 ? daysLeft : 0,
                "overdue", daysLeft < 0,
                "note", "A 股法定披露截止日，遇节假日顺延，以交易所公告为准"
        );
    }
}
