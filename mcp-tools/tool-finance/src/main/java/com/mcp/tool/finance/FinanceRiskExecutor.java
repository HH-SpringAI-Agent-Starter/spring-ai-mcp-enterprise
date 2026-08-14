package com.mcp.tool.finance;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金融风险评分工具 (企业场景模板 - 金融 · 二期)
 *
 * 提供投研/信贷场景的多维财务风险评分：
 * - 偿债风险（资产负债率）
 * - 流动性风险（流动比率）
 * - 盈利风险（毛利率 / 净利率）
 * - 成长风险（营收增速）
 * - 现金流风险（经营现金流 / 净利润）
 *
 * 综合输出 0-100 风险分 + 风险等级 + 分维度诊断，供智能体直接消费。
 * 面向场景：信贷审批助手、投顾风控、尽调分析 Agent。
 */
@Component
public class FinanceRiskExecutor implements McpToolExecutor {

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of(
                "type", "string",
                "description", "操作类型: score(综合风险评分) | diagnose(单维度风险诊断)"
        ));
        properties.put("params", Map.of(
                "type", "object",
                "description", "财务参数(JSON对象): " +
                        "debtRatioPct(资产负债率%) / currentRatio(流动比率) / " +
                        "grossMarginPct(毛利率%) / netMarginPct(净利率%) / " +
                        "revenueGrowthPct(营收增速%) / ocfToNetProfit(经营现金流/净利润)"
        ));

        return new ToolDefinition(
                "finance_risk", "风险评分",
                "基于偿债/流动性/盈利/成长/现金流五维度的财务风险综合评分(0-100)与诊断，面向信贷/投顾/尽调智能体场景",
                "finance", "1.0.0", null, true, "admin,user", 5000, 20,
                Map.of("type", "object", "properties", properties,
                        "required", List.of("action")), null
        );
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String action = params != null && params.get("action") != null
                ? String.valueOf(params.get("action")).toLowerCase() : "";

        Object rawParams = params != null ? params.get("params") : null;
        Map<String, Object> p = new LinkedHashMap<>();
        if (rawParams instanceof Map<?, ?> m) {
            m.forEach((k, v) -> p.put(String.valueOf(k), v));
        }

        try {
            return switch (action) {
                case "score" -> Mono.just(score(p));
                case "diagnose" -> Mono.just(diagnose(p));
                default -> Mono.just(Map.of("success", false, "error",
                        "不支持的 action: " + action + "，可选 score/diagnose"));
            };
        } catch (Exception e) {
            return Mono.just(Map.of("success", false, "error", "执行异常: " + e.getMessage()));
        }
    }

    private double num(Map<String, Object> p, String key, double defaultValue) {
        Object v = p.get(key);
        if (v == null) return defaultValue;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + key + " 不是有效数字: " + v);
        }
    }

    /**
     * 综合风险评分：0-100，分数越高风险越大。
     * 各维度按风险贡献度加权：偿债30% 流动性20% 盈利20% 成长15% 现金流15%。
     */
    private Map<String, Object> score(Map<String, Object> p) {
        double debtRatio = num(p, "debtRatioPct", 0);
        double currentRatio = num(p, "currentRatio", 0);
        double grossMargin = num(p, "grossMarginPct", 0);
        double netMargin = num(p, "netMarginPct", 0);
        double growth = num(p, "revenueGrowthPct", 0);
        double ocf = num(p, "ocfToNetProfit", 0);

        // 各维度风险分（0-100）
        double debtRisk = clamp(debtRatio <= 40 ? debtRatio / 40 * 20
                : 20 + (debtRatio - 40) / 40 * 80, 0, 100);
        double liquidityRisk = clamp(currentRatio <= 0 ? 50
                : (currentRatio >= 2 ? 0 : (2 - currentRatio) / 2 * 100), 0, 100);
        double profitRisk = clamp(netMargin <= 0 ? 100
                : (netMargin >= 15 ? 0 : (15 - netMargin) / 15 * 100), 0, 100);
        double growthRisk = clamp(growth <= -10 ? 100
                : (growth >= 15 ? 0 : (15 - growth) / 25 * 100), 0, 100);
        double cashRisk = clamp(ocf <= 0 ? 100
                : (ocf >= 1.0 ? 0 : (1.0 - ocf) / 1.0 * 100), 0, 100);

        double total = debtRisk * 0.30 + liquidityRisk * 0.20 + profitRisk * 0.20
                + growthRisk * 0.15 + cashRisk * 0.15;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("debt", Map.of("score", round1(debtRisk), "weight", 0.30,
                "input", debtRatio + "% 资产负债率"));
        detail.put("liquidity", Map.of("score", round1(liquidityRisk), "weight", 0.20,
                "input", currentRatio + " 流动比率"));
        detail.put("profit", Map.of("score", round1(profitRisk), "weight", 0.20,
                "input", netMargin + "% 净利率"));
        detail.put("growth", Map.of("score", round1(growthRisk), "weight", 0.15,
                "input", growth + "% 营收增速"));
        detail.put("cashflow", Map.of("score", round1(cashRisk), "weight", 0.15,
                "input", ocf + " 经营现金流/净利润"));

        return Map.of(
                "success", true,
                "action", "score",
                "riskScore", round1(total),
                "riskLevel", level(total),
                "dimensions", detail,
                "note", "评分模型为演示级加权模型，生产环境请结合行业基准与专家规则校准"
        );
    }

    /** 单维度诊断：快速给出某一维度的风险结论 */
    private Map<String, Object> diagnose(Map<String, Object> p) {
        String dimension = String.valueOf(p.getOrDefault("dimension", "")).toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("action", "diagnose");
        result.put("dimension", dimension);

        switch (dimension) {
            case "debt" -> {
                double v = num(p, "debtRatioPct", 0);
                result.put("input", v + "% 资产负债率");
                result.put("riskScore", round1(clamp(v <= 40 ? v / 40 * 20
                        : 20 + (v - 40) / 40 * 80, 0, 100)));
                result.put("advice", v > 70 ? "高杠杆，偿债压力大，建议关注融资结构与再融资能力"
                        : v > 40 ? "杠杆处于中等水平，需结合行业对比评估"
                        : "财务杠杆健康，偿债风险低");
            }
            case "liquidity" -> {
                double v = num(p, "currentRatio", 0);
                result.put("input", v + " 流动比率");
                result.put("riskScore", round1(clamp(v <= 0 ? 50
                        : (v >= 2 ? 0 : (2 - v) / 2 * 100), 0, 100)));
                result.put("advice", v >= 2 ? "短期偿债能力强，流动性充裕"
                        : v >= 1 ? "短期偿债能力基本达标，注意营运资金管理"
                        : "流动比率偏低，存在短期偿付压力");
            }
            case "profit" -> {
                double v = num(p, "netMarginPct", 0);
                result.put("input", v + "% 净利率");
                result.put("riskScore", round1(clamp(v <= 0 ? 100
                        : (v >= 15 ? 0 : (15 - v) / 15 * 100), 0, 100)));
                result.put("advice", v > 15 ? "盈利能力强，处于行业较优水平"
                        : v > 0 ? "盈利水平一般，需关注成本控制与价格竞争"
                        : "亏损状态，盈利风险高，需重点核查可持续性");
            }
            case "growth" -> {
                double v = num(p, "revenueGrowthPct", 0);
                result.put("input", v + "% 营收增速");
                result.put("riskScore", round1(clamp(v <= -10 ? 100
                        : (v >= 15 ? 0 : (15 - v) / 25 * 100), 0, 100)));
                result.put("advice", v >= 15 ? "成长性良好，业务扩张动力足"
                        : v >= 0 ? "增长平稳，需关注行业天花板"
                        : "营收负增长，成长风险较高，需核查业务萎缩原因");
            }
            case "cashflow" -> {
                double v = num(p, "ocfToNetProfit", 0);
                result.put("input", v + " 经营现金流/净利润");
                result.put("riskScore", round1(clamp(v <= 0 ? 100
                        : (v >= 1.0 ? 0 : (1.0 - v) / 1.0 * 100), 0, 100)));
                result.put("advice", v >= 1.0 ? "利润含金量高，现金流健康"
                        : v > 0 ? "利润含金量一般，关注应收与存货占款"
                        : "经营现金流为负/利润未转化为现金，盈利质量风险高");
            }
            default -> {
                return Map.of("success", false, "error",
                        "不支持的 dimension: " + dimension + "，可选 debt/liquidity/profit/growth/cashflow");
            }
        }
        return result;
    }

    private String level(double score) {
        if (score < 20) return "低风险";
        if (score < 45) return "中低风险";
        if (score < 70) return "中高风险";
        return "高风险";
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
