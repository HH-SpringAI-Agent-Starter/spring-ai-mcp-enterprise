package com.mcp.integration.springai;

import com.mcp.enterprise.core.tool.McpToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Spring AI 工具桥接自动配置（V1.23）
 *
 * <p>在存在企业 {@code McpToolManager} 的前提下，自动收集 Spring AI 工具并批量注册进企业 MCP
 * 注册中心。注册走的是 {@code McpToolManager.registerExecutor}，因此自动获得框架已有的
 * RBAC / 限流 / 审计 / 工具级 Scope / 健康检查等全部治理能力。</p>
 *
 * <p>关闭方式：{@code mcp.springai-tools.enabled=false}</p>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.mcp.enterprise.autoconfigure.McpEnterpriseAutoConfiguration")
@ConditionalOnClass({ToolCallback.class, McpToolManager.class})
@ConditionalOnBean(McpToolManager.class)
@ConditionalOnProperty(prefix = "mcp.springai-tools", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SpringAiToolsProperties.class)
public class SpringAiToolsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SpringAiToolsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SpringAiToolCollector springAiToolCollector(SpringAiToolsProperties properties) {
        return new SpringAiToolCollector(properties);
    }

    @Bean
    public SpringAiToolRegistrar springAiToolRegistrar(McpToolManager toolManager,
                                                        SpringAiToolCollector collector,
                                                        ListableBeanFactory beanFactory) {
        return new SpringAiToolRegistrar(toolManager, collector, beanFactory);
    }

    /**
     * 在所有单例 Bean 创建完成后执行注册，确保 @Tool Bean / ToolCallback Bean 已就绪。
     */
    public static class SpringAiToolRegistrar implements SmartInitializingSingleton {

        private static final Logger log = LoggerFactory.getLogger(SpringAiToolRegistrar.class);

        private final McpToolManager toolManager;
        private final SpringAiToolCollector collector;
        private final ListableBeanFactory beanFactory;

        public SpringAiToolRegistrar(McpToolManager toolManager,
                                      SpringAiToolCollector collector,
                                      ListableBeanFactory beanFactory) {
            this.toolManager = toolManager;
            this.collector = collector;
            this.beanFactory = beanFactory;
        }

        @Override
        public void afterSingletonsInstantiated() {
            List<SpringAiToolCallbackExecutor> executors = collector.collect(beanFactory);
            if (executors.isEmpty()) {
                log.info("⚡ Spring AI 工具桥接：未发现 @Tool / ToolCallback 工具，跳过注册");
                return;
            }
            log.info("⚡ Spring AI 工具桥接：发现 {} 个 Spring AI 工具，自动注册为企业 MCP 工具（RBAC/限流/审计/Scope 已生效）",
                    executors.size());
            for (SpringAiToolCallbackExecutor executor : executors) {
                try {
                    toolManager.registerExecutor(executor);
                } catch (Exception e) {
                    log.warn("注册 Spring AI 工具 [{}] 失败: {}",
                            executor.getDefinition().getName(), e.getMessage());
                }
            }
        }
    }
}