package com.example.mcp.audit.ropa;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GDPR ROPA（Records of Processing Activities）七字段导出参考实现
 * （V1.66 新增，兑现 V1.65 待办 ⑤；REFERENCE ONLY，不进 Maven reactor）。
 *
 * <p><b>为什么要做：</b>Peliqan《GDPR-Compliant MCP Servers: The 2026 Procurement Reference》
 * 指出 ROPA 是 DPA 问询、网络保险承保、SOC 2 审计都会索要的工件，要求 MCP server
 * 自动产出七类元数据；自动化每年可为 DPO 省 40–80 小时。V1.65 已把七字段写进
 * {@code docs/enterprise-rfp-checklist.md} 的采购对照表，本文件把它变成可运行导出。</p>
 *
 * <p><b>与国内招标的关系：</b>广发证券 13322 号公告的「工具定义与描述 / 服务行为自动留痕、
 * 日志不可篡改 / 数据权限按客户经理归属严格隔离」正好落在 purposeTag、
 * securityControls、dataSubjects 三字段上 —— 采购应答里写「符合等保」不如
 * 直接给出这份由哪个类产生、怎么导出的 JSON。</p>
 *
 * <p><b>数据来源映射（集成时替换）：</b></p>
 * <pre>
 *   purposeTag       <- ToolDefinition / SkillSpec 元数据
 *   dataCategories   <- 连接器注册时的数据分级标签
 *   dataSubjects     <- TenantContext / McpTenantFilter 的主体类别
 *   recipients       <- mcp-gateway 联邦上游 + mcp-integrations 出站记录
 *   securityControls <- JdbcGovernanceAuditSink / McpAuditLogger (append-only)
 * </pre>
 *
 * <p>零外部依赖（纯 JDK），可直接 {@code javac RopaExportService.java && java ...} 自检。
 * 生产集成建议把 {@link #collect()} 换成从注册中心 + 审计库查询，并把
 * {@link #exportJson()} 挂到管理端点（导出本身也要落审计）。</p>
 */
public final class RopaExportService {

    /** Peliqan 采购参考要求的七类元数据。 */
    public record RopaEntry(
            String toolName,
            String purposeTag,                 // 1. per-tool purpose tag
            List<String> dataCategories,       // 2. per-connector 数据分类
            List<String> dataSubjects,         // 3. 数据主体类别
            List<String> recipients,           // 4. 接收方（LLM / 下游 SaaS / 审计目的地）
            String thirdCountryTransfer,      // 5. 第三国转移
            String sccReference,              // 5. SCC 编号
            int retentionDays,                // 6. 保留期
            List<String> securityControls,    // 7. 安全措施
            String lastReviewed
    ) {
        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (isBlank(toolName)) errors.add("toolName");
            if (isBlank(purposeTag)) errors.add("purposeTag(1)");
            if (isBlank(dataCategories)) errors.add("dataCategories(2)");
            if (isBlank(dataSubjects)) errors.add("dataSubjects(3)");
            if (isBlank(recipients)) errors.add("recipients(4)");
            if (isBlank(thirdCountryTransfer)) errors.add("thirdCountryTransfer(5)");
            if (retentionDays <= 0) errors.add("retentionDays(6)");
            if (isBlank(securityControls)) errors.add("securityControls(7)");
            return errors;
        }
        private static boolean isBlank(String s) { return s == null || s.isBlank(); }
        private static boolean isBlank(List<?> l) { return l == null || l.isEmpty(); }
    }

    private final List<RopaEntry> entries;

    public RopaExportService(List<RopaEntry> entries) {
        this.entries = List.copyOf(Objects.requireNonNull(entries));
    }

    /** 导出前强制七字段自检：缺一不可，避免交出一份 DPA 会退回来的半成品。 */
    public void assertExportable() {
        for (RopaEntry e : entries) {
            List<String> missing = e.validate();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("ROPA incomplete for " + e.toolName() + ": " + missing);
            }
        }
    }

    public List<RopaEntry> collect() { return entries; }

    /** 可直接交给 DPA / 保险 / SOC 2 审计的 JSON。 */
    public String exportJson() {
        assertExportable();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"schema\": \"mcp-ropa/v1\",\n")
          .append("  \"generatedAt\": \"").append(LocalDate.now()).append("\",\n")
          .append("  \"framework\": \"GDPR Art.30 / 等保2.0 三级审计留痕\",\n")
          .append("  \"processingActivities\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            RopaEntry e = entries.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("toolName", e.toolName());
            row.put("purposeTag", e.purposeTag());
            row.put("dataCategories", e.dataCategories());
            row.put("dataSubjects", e.dataSubjects());
            row.put("recipients", e.recipients());
            row.put("thirdCountryTransfer", e.thirdCountryTransfer());
            row.put("sccReference", e.sccReference());
            row.put("retentionDays", e.retentionDays());
            row.put("securityControls", e.securityControls());
            row.put("lastReviewed", e.lastReviewed());
            sb.append("    ").append(toJson(row));
            if (i < entries.size() - 1) sb.append(',');
            sb.append('\n');
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    /** 给采购/法务用 Excel 打开的 CSV（字段顺序与七字段一致）。 */
    public String exportCsv() {
        assertExportable();
        StringBuilder sb = new StringBuilder();
        sb.append("toolName,purposeTag,dataCategories,dataSubjects,recipients,")
          .append("thirdCountryTransfer,sccReference,retentionDays,securityControls,lastReviewed\n");
        for (RopaEntry e : entries) {
            sb.append(csv(e.toolName())).append(',').append(csv(e.purposeTag())).append(',')
              .append(csv(String.join(" | ", e.dataCategories()))).append(',')
              .append(csv(String.join(" | ", e.dataSubjects()))).append(',')
              .append(csv(String.join(" | ", e.recipients()))).append(',')
              .append(csv(e.thirdCountryTransfer())).append(',').append(csv(e.sccReference())).append(',')
              .append(e.retentionDays()).append(',')
              .append(csv(String.join(" | ", e.securityControls()))).append(',')
              .append(csv(e.lastReviewed())).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 极简 JSON 序列化（避免为 examples 引入 Jackson / Gson 依赖）
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static String toJson(Object o) {
        if (o == null) return "null";
        if (o instanceof String s) return "\"" + esc(s) + "\"";
        if (o instanceof Number n) return n.toString();
        if (o instanceof List<?> l) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) b.append(", ");
                b.append(toJson(l.get(i)));
            }
            return b.append(']').toString();
        }
        if (o instanceof Map<?, ?> m) {
            StringBuilder b = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(", ");
                first = false;
                b.append('"').append(esc(String.valueOf(e.getKey()))).append("\": ")
                 .append(toJson(e.getValue()));
            }
            return b.append('}').toString();
        }
        return "\"" + esc(o.toString()) + "\"";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String csv(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ------------------------------------------------------------------
    // 样例 + 对照组自检
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        RopaExportService svc = new RopaExportService(Arrays.asList(
            new RopaEntry("customer_portfolio_query",
                "客户资产查询（投顾业务必需）",
                List.of("客户身份信息", "资产持仓", "交易流水"),
                List.of("券商客户", "客户经理"),
                List.of("内部 LLM 网关（私有化）", "券商核心交易系统", "append-only 审计库"),
                "无（数据不出境，私有化部署）", "N/A", 1095,
                List.of("传输 TLS1.3", "静态 AES-256", "RBAC + 按客户经理归属隔离",
                        "审计日志 append-only 且防篡改", "MFA", "字段级脱敏"),
                "2026-11-08"),
            new RopaEntry("market_news_search",
                "公开金融资讯检索",
                List.of("公开市场数据"),
                List.of("不识别自然人"),
                List.of("外部资讯供应商", "append-only 审计库"),
                "资讯供应商跨境只读接口", "SCC-2021/914 Module Two", 365,
                List.of("传输 TLS1.3", "出站白名单", "审计日志 append-only", "只读 scope"),
                "2026-11-08"),
            new RopaEntry("skill_registry_export",
                "技能包元数据导出（运维）",
                List.of("工具元数据", "操作者账号"),
                List.of("内部运维人员"),
                List.of("内部对象存储", "append-only 审计库"),
                "无", "N/A", 180,
                List.of("传输 TLS1.3", "静态 AES-256", "RBAC", "双人复核", "审计日志 append-only"),
                "2026-11-08")
        ));

        String json = svc.exportJson();
        if (!json.contains("\"processingActivities\"") || !json.contains("\"schema\": \"mcp-ropa/v1\"")) {
            throw new AssertionError("ROPA JSON 结构缺失");
        }
        if (!svc.exportCsv().contains("append-only 审计库")) {
            throw new AssertionError("ROPA CSV 缺少审计接收方");
        }
        // 对照组：七字段缺一必须被拦下，不能导出一份 DPA 会退回的半成品
        boolean blocked = false;
        try {
            new RopaExportService(List.of(new RopaEntry(
                "bad_tool", "", List.of(), List.of(), List.of(), "", "", 0, List.of(), "2026-11-08"
            ))).exportJson();
        } catch (IllegalStateException e) {
            blocked = true;
        }
        if (!blocked) throw new AssertionError("不完整的 ROPA 记录被静默导出");

        System.out.println("[RopaExportService] self-test OK (3 activities + 1 negative control)");
        System.out.print(json);
        System.out.println(new String(svc.exportCsv().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }
}
