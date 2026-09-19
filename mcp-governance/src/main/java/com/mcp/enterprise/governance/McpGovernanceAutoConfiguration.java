package com.mcp.enterprise.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

/**
 * V1.26 MCP Governance 自动配置（人类在环人工审批 / 风险分级 / 敏感数据脱敏）。
 *
 * <p>默认开启（{@code mcp.enterprise.governance.enabled=false} 关闭）。</p>
 *
 * <p>装配的组件（全部 {@code @ConditionalOnMissingBean}，可被宿主应用覆盖）：</p>
 * <ul>
 *   <li>{@link SensitiveDataRedactor}：PII/Secret 就地脱敏（审计链路打码基线）；</li>
 *   <li>{@link McpToolRiskClassifier}：工具风险分级（显式覆盖 → 分类语义 → 关键词启发式 → 默认等级）；</li>
 *   <li>{@link ApprovalStore}（默认 {@link InMemoryApprovalStore}，有界内存）；</li>
 *   <li>{@link McpApprovalService}：审批创建/批准/拒绝/一次性消费（Fail-closed）；</li>
 *   <li>{@link McpGovernanceGuard}：判定核心（deny-tiers 硬拒 / require-approval-tiers 审批 / 其余放行）；</li>
 *   <li>{@link McpGovernanceAuditSink}（默认 {@link InMemoryGovernanceAuditSink}）；</li>
 *   <li>{@link McpGovernanceAdminController}：审批/审计管理 REST API（/api/admin/governance）；</li>
 *   <li>{@link McpGovernanceFilter}：JSON-RPC 入口治理过滤器（仅 Web 应用 + {@code enforce=true} 时挂载）。</li>
 * </ul>
 *
 * <p>灰度策略：出厂默认 {@code enforce=false} —— 模块只做「登记 + 审计 + 审批队列」，
 * 不拦截任何调用；企业灰度验证后置 {@code enforce=true} 开启运行时强制。</p>
 */
@AutoConfiguration
@ConditionalOnClass(RestController.class)
@ConditionalOnProperty(prefix = "mcp.enterprise.governance", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpGovernanceProperties.class)
public class McpGovernanceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpGovernanceAutoConfiguration.class);

    /** 过滤器次序：租户过滤器（HIGHEST_PRECEDENCE+10）之后、业务过滤器之前。 */
    public static final int GOVERNANCE_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    @Bean
    @ConditionalOnMissingBean
    public SensitiveDataRedactor sensitiveDataRedactor(McpGovernanceProperties properties) {
        return new SensitiveDataRedactor(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolRiskClassifier mcpToolRiskClassifier(McpGovernanceProperties properties) {
        return new McpToolRiskClassifier(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalStore mcpApprovalStore(McpGovernanceProperties properties,
                                          ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        // V1.28: store=jdbc 且存在数据源时启用 JDBC 审批存储（多实例共享审批状态）
        if ("jdbc".equalsIgnoreCase(properties.getApproval().getStore())) {
            JdbcTemplate jdbc = jdbcTemplateProvider.getIfAvailable();
            if (jdbc == null) {
                log.warn("?? [V1.28] approval.store=jdbc requested but no JdbcTemplate/DataSource found; "
                        + "falling back to in-memory ApprovalStore");
                return new InMemoryApprovalStore(properties.getApproval().getMaxPending());
            }
            JdbcApprovalStore store = new JdbcApprovalStore(jdbc, properties.getApproval().getTable());
            if (properties.getApproval().isInitSchema()) {
                store.initSchema();
            }
            log.info("?? [V1.28] Approval JDBC persistence enabled (table={})", properties.getApproval().getTable());
            return store;
        }
        int maxPending = properties.getApproval().getMaxPending();
        return new InMemoryApprovalStore(maxPending);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpApprovalService mcpApprovalService(ApprovalStore approvalStore,
                                                 SensitiveDataRedactor redactor,
                                                 McpGovernanceProperties properties) {
        return new McpApprovalService(approvalStore, redactor, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpGovernanceAuditSink mcpGovernanceAuditSink(McpGovernanceProperties properties) {
        return new InMemoryGovernanceAuditSink(
                properties.getAudit().getMaxEvents(),
                properties.getAudit().isLogToSlf4j());
    }

    @Bean
    @ConditionalOnMissingBean
    public McpGovernanceGuard mcpGovernanceGuard(McpGovernanceProperties properties,
                                                 McpToolRiskClassifier classifier,
                                                 McpApprovalService approvalService) {
        return new McpGovernanceGuard(properties, classifier, approvalService);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpGovernanceAdminController mcpGovernanceAdminController(McpApprovalService approvalService,
                                                                     McpGovernanceAuditSink auditSink,
                                                                     McpGovernanceGuard guard,
                                                                     SensitiveDataRedactor redactor,
                                                                     McpGovernanceProperties properties) {
        return new McpGovernanceAdminController(approvalService, auditSink, guard, redactor, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "mcpGovernanceFilterRegistration")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<McpGovernanceFilter> mcpGovernanceFilterRegistration(
            McpGovernanceProperties properties, McpGovernanceGuard guard,
            McpGovernanceAuditSink auditSink, SensitiveDataRedactor redactor,
            ObjectMapper objectMapper) {
        McpGovernanceFilter filter = new McpGovernanceFilter(properties, guard, auditSink, redactor, objectMapper);
        FilterRegistrationBean<McpGovernanceFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(GOVERNANCE_FILTER_ORDER);
        // 过滤器内部再按 message-paths + enforce 自判（shouldNotFilter），这里全路径注册即可
        registration.setEnabled(properties.isEnabled());
        if (properties.isEnforce()) {
            log.info("🛡 [V1.26] MCP Governance 强制模式已开启（enforce=true）——高等级工具调用将被拦截并进入人工审批");
        } else {
            log.info("🛡 [V1.26] MCP Governance 灰度模式（enforce=false）——仅登记风险与审批请求，不拦截调用");
        }
        return registration;
    }
}