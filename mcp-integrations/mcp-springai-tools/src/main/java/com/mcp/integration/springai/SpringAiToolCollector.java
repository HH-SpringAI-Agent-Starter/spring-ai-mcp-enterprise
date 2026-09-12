package com.mcp.integration.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Spring AI 工具收集器（V1.23）
 *
 * <p>从 Spring 容器中发现所有可暴露为 MCP 工具的 Spring AI 工具，来源覆盖三类：</p>
 * <ol>
 *   <li>直接注册的 {@link ToolCallback} Bean（含 {@code FunctionToolCallback}）；</li>
 *   <li>{@link ToolCallbackProvider} Bean（如 Spring AI MCP Client 动态发现的远端工具）；</li>
 *   <li>带 {@code @Tool} 注解方法的普通 Spring Bean（Spring AI 最主流的写法）。</li>
 * </ol>
 *
 * <p>按最终注册名去重，重复名仅保留首个并告警，避免多来源工具冲突覆盖。</p>
 */
public class SpringAiToolCollector {

    private static final Logger log = LoggerFactory.getLogger(SpringAiToolCollector.class);

    private final SpringAiToolsProperties properties;

    public SpringAiToolCollector(SpringAiToolsProperties properties) {
        this.properties = properties;
    }

    /**
     * 收集并适配 Spring AI 工具。
     *
     * @param beanFactory Spring 容器
     * @return 已去重的企业 MCP 工具执行器列表
     */
    public List<SpringAiToolCallbackExecutor> collect(ListableBeanFactory beanFactory) {
        Map<String, SpringAiToolCallbackExecutor> collected = new LinkedHashMap<>();

        collectToolCallbackBeans(beanFactory, collected);

        if (properties.isIncludeToolCallbackProviders()) {
            collectProviderBeans(beanFactory, collected);
        }

        if (properties.isScanToolAnnotatedBeans()) {
            collectToolAnnotatedBeans(beanFactory, collected);
        }

        return new ArrayList<>(collected.values());
    }

    private void collectToolCallbackBeans(ListableBeanFactory beanFactory,
                                          Map<String, SpringAiToolCallbackExecutor> collected) {
        for (String beanName : beanFactory.getBeanNamesForType(ToolCallback.class, false, false)) {
            try {
                addIfAbsent(collected, beanFactory.getBean(beanName, ToolCallback.class));
            } catch (Exception e) {
                log.warn("读取 ToolCallback Bean [{}] 失败，已跳过: {}", beanName, e.getMessage());
            }
        }
    }

    private void collectProviderBeans(ListableBeanFactory beanFactory,
                                      Map<String, SpringAiToolCallbackExecutor> collected) {
        for (String beanName : beanFactory.getBeanNamesForType(ToolCallbackProvider.class, false, false)) {
            try {
                ToolCallbackProvider provider = beanFactory.getBean(beanName, ToolCallbackProvider.class);
                FunctionCallback[] callbacks = provider.getToolCallbacks();
                if (callbacks == null) {
                    continue;
                }
                for (FunctionCallback callback : callbacks) {
                    if (callback instanceof ToolCallback toolCallback) {
                        addIfAbsent(collected, toolCallback);
                    }
                }
            } catch (Exception e) {
                log.warn("读取 ToolCallbackProvider Bean [{}] 失败，已跳过: {}", beanName, e.getMessage());
            }
        }
    }

    private void collectToolAnnotatedBeans(ListableBeanFactory beanFactory,
                                           Map<String, SpringAiToolCallbackExecutor> collected) {
        // 兼容两种 Bean 来源：BeanDefinition 注册的 Bean + 手动 registerSingleton 的 Bean
        // （Spring 6.x 起 registerSingleton 不再生成 BeanDefinition，需合并 getSingletonNames）
        Set<String> beanNames = new LinkedHashSet<>(Arrays.asList(beanFactory.getBeanDefinitionNames()));
        if (beanFactory instanceof SingletonBeanRegistry singletonBeanRegistry) {
            beanNames.addAll(Arrays.asList(singletonBeanRegistry.getSingletonNames()));
        }
        for (String beanName : beanNames) {
            Class<?> type;
            try {
                type = beanFactory.getType(beanName, false);
            } catch (Exception e) {
                continue;
            }
            if (type == null || ToolCallback.class.isAssignableFrom(type) || !hasToolAnnotatedMethod(type)) {
                continue;
            }
            try {
                Object bean = beanFactory.getBean(beanName);
                for (ToolCallback callback : ToolCallbacks.from(bean)) {
                    addIfAbsent(collected, callback);
                }
            } catch (Exception e) {
                log.warn("扫描 @Tool Bean [{}] 失败，已跳过: {}", beanName, e.getMessage());
            }
        }
    }

    private boolean hasToolAnnotatedMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }

    private void addIfAbsent(Map<String, SpringAiToolCallbackExecutor> collected, ToolCallback callback) {
        SpringAiToolCallbackExecutor executor = new SpringAiToolCallbackExecutor(callback, properties);
        String registeredName = executor.getDefinition().getName();
        if (collected.containsKey(registeredName)) {
            log.warn("⚠️ Spring AI 工具重名，已忽略后出现的同名工具: {}", registeredName);
            return;
        }
        collected.put(registeredName, executor);
    }
}
