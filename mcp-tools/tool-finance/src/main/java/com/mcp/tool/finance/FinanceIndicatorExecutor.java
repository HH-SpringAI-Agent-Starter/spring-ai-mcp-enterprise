package com.mcp.tool.finance;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金融财务指标计算工具 (企业场景模板 - 金融)
 *
 * 提供投研/财务分析常用指标计算：
 * - CAGR 复合增长率
 * - ROE 净资产收益率推算（净利润/净资产）
 * - PEG 估值指标
 * - 复利终值 / 定投终值
 * - 毛利率 / 净利率
 *
 * 面向金融机构智能体场景：研报助手、投顾机器人、尽调分析 Agent。
 */
@Component
public class FinanceIndicatorExecutor implements McpToolExecutor {

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("indicator", Map.of(
                "type", "string",
                "description", "指标类型: cagr | roe | peg | compound | annuity | margin"
        ));
        properties.put("params", Map.of(
                "type", "object",
                "description", "指标参数(JSON对象): " +
                        "cagr:{beginValue,endValue,years} | roe:{netProfit,equity} | " +
                        "peg:{pe,earningsGrowthPct} | compound:{principal,annualRatePct,years} | " +
                        "annuity:{monthly,annualRatePct,years} | margin:{revenue,cost,netProfit}"
        ));

        return new ToolDefinition(
                "finance_indicator", "财务指标计算器",
                "计算 CAGR/ROE/PEG/复利终值/定投终值/毛利率等投研常用财务指标，面向金融智能体场景",
                "finance", "1.0.0", null, true, "admin,user", 5000, 20,
                Map.of("type", "object", "properties", properties,
                        "required", List.of("indicator", "params")), null
        );
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String indicator = params != null && params.get("indicator") != null
                ? String.valueOf(params.get("indicator")).toLowerCase() : "";

        Object rawParams = params != null ? params.get("params") : null;
        Map<String, Object> p = new LinkedHashMap<>();
        if (rawParams instanceof Map<?, ?> m) {
            m.forEach((k, v) -> p.put(String.valueOf(k), v));
        }

        if (indicator.isBlank()) {
            return Mono.just(Map.of("success", false, "error", "indicator 不能为空"));
        }

        try {
            return switch (indicator) {
                case "cagr" -> Mono.just(calcCagr(p));
                case "roe" -> Mono.just(calcRoe(p));
                case "peg" -> Mono.just(calcPeg(p));
                case "compound" -> Mono.just(calcCompound(p));
                case "annuity" -> Mono.just(calcAnnuity(p));
                case "margin" -> Mono.just(calcMargin(p));
                default -> Mono.just(Map.of("success", false, "error",
                        "不支持的指标: " + indicator + "，可选 cagr/roe/peg/compound/annuity/margin"));
            };
        } catch (Exception e) {
            return Mono.just(Map.of("success", false, "error", "计算异常: " + e.getMessage()));
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

    private Map<String, Object> calcCagr(Map<String, Object> p) {
        double begin = num(p, "beginValue", 0);
        double end = num(p, "endValue", 0);
        double years = num(p, "years", 0);
        if (begin <= 0) throw new IllegalArgumentException("beginValue 必须大于 0");
        if (years <= 0) throw new IllegalArgumentException("years 必须大于 0");
        double cagr = Math.pow(end / begin, 1.0 / years) - 1;
        return Map.of(
                "success", true,
                "indicator", "CAGR",
                "cagr", round4(cagr),
                "cagrPct", round2(cagr * 100),
                "formula", "(endValue/beginValue)^(1/years)-1",
                "input", Map.of("beginValue", begin, "endValue", end, "years", years)
        );
    }

    private Map<String, Object> calcRoe(Map<String, Object> p) {
        double netProfit = num(p, "netProfit", 0);
        double equity = num(p, "equity", 0);
        if (equity <= 0) throw new IllegalArgumentException("equity(净资产) 必须大于 0");
        double roe = netProfit / equity;
        return Map.of(
                "success", true,
                "indicator", "ROE",
                "roe", round4(roe),
                "roePct", round2(roe * 100),
                "formula", "netProfit/equity",
                "input", Map.of("netProfit", netProfit, "equity", equity)
        );
    }

    private Map<String, Object> calcPeg(Map<String, Object> p) {
        double pe = num(p, "pe", 0);
        double growth = num(p, "earningsGrowthPct", 0);
        if (pe <= 0) throw new IllegalArgumentException("pe 必须大于 0");
        double peg = pe / growth;
        return Map.of(
                "success", true,
                "indicator", "PEG",
                "peg", round2(peg),
                "assessment", peg < 1 ? "低估(相对成长)" : (peg <= 1.5 ? "合理" : "偏高"),
                "formula", "pe/earningsGrowthPct",
                "input", Map.of("pe", pe, "earningsGrowthPct", growth)
        );
    }

    private Map<String, Object> calcCompound(Map<String, Object> p) {
        double principal = num(p, "principal", 0);
        double rate = num(p, "annualRatePct", 0) / 100.0;
        double years = num(p, "years", 0);
        if (years <= 0) throw new IllegalArgumentException("years 必须大于 0");
        double futureValue = principal * Math.pow(1 + rate, years);
        return Map.of(
                "success", true,
                "indicator", "复利终值",
                "futureValue", round2(futureValue),
                "interest", round2(futureValue - principal),
                "formula", "principal*(1+rate)^years",
                "input", Map.of("principal", principal, "annualRatePct", rate * 100, "years", years)
        );
    }

    private Map<String, Object> calcAnnuity(Map<String, Object> p) {
        double monthly = num(p, "monthly", 0);
        double annualRate = num(p, "annualRatePct", 0) / 100.0;
        double years = num(p, "years", 0);
        if (years <= 0) throw new IllegalArgumentException("years 必须大于 0");
        double r = annualRate / 12.0;
        int n = (int) (years * 12);
        double futureValue = r == 0 ? monthly * n : monthly * ((Math.pow(1 + r, n) - 1) / r);
        return Map.of(
                "success", true,
                "indicator", "定投终值",
                "futureValue", round2(futureValue),
                "totalInvested", round2(monthly * n),
                "interest", round2(futureValue - monthly * n),
                "formula", "monthly*((1+r)^n-1)/r, r=annualRatePct/12",
                "input", Map.of("monthly", monthly, "annualRatePct", annualRate * 100, "years", years)
        );
    }

    private Map<String, Object> calcMargin(Map<String, Object> p) {
        double revenue = num(p, "revenue", 0);
        double cost = num(p, "cost", 0);
        double netProfit = num(p, "netProfit", 0);
        if (revenue <= 0) throw new IllegalArgumentException("revenue(营业收入) 必须大于 0");
        double grossMargin = (revenue - cost) / revenue;
        double netMargin = netProfit / revenue;
        return Map.of(
                "success", true,
                "indicator", "利润率",
                "grossMarginPct", round2(grossMargin * 100),
                "netMarginPct", round2(netMargin * 100),
                "formula", "gross=(revenue-cost)/revenue, net=netProfit/revenue",
                "input", Map.of("revenue", revenue, "cost", cost, "netProfit", netProfit)
        );
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
