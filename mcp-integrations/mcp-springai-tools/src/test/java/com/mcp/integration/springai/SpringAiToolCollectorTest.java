package com.mcp.integration.springai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring AI 工具收集器测试（V1.23）
 *
 * <p>使用裸 {@link DefaultListableBeanFactory}（无需 Spring Boot 上下文）验证三类来源
 * 的收集与按名去重。</p>
 */
class SpringAiToolCollectorTest {

    static class WeatherTools {
        @Tool(name = "getWeather", description = "查询天气")
        public String getWeather(@ToolParam(description = "城市") String city) {
            return "{\"temp\":22}";
        }
    }

    static class EmailTools {
        @Tool(name = "sendEmail", description = "发送邮件")
        public String sendEmail(@ToolParam(description = "收件人") String to,
                                @ToolParam(description = "内容") String body) {
            return "{\"sent\":true}";
        }
    }

    @Test
    void collectsToolAnnotatedBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("weatherTools", new WeatherTools());
        beanFactory.registerSingleton("emailTools", new EmailTools());

        List<SpringAiToolCallbackExecutor> executors =
                new SpringAiToolCollector(new SpringAiToolsProperties()).collect(beanFactory);

        assertEquals(2, executors.size());
        assertTrue(executors.stream().anyMatch(e -> "getWeather".equals(e.getDefinition().getName())));
        assertTrue(executors.stream().anyMatch(e -> "sendEmail".equals(e.getDefinition().getName())));
    }

    @Test
    void collectsDirectToolCallbackBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("weatherCallback", ToolCallbacks.from(new WeatherTools())[0]);

        List<SpringAiToolCallbackExecutor> executors =
                new SpringAiToolCollector(new SpringAiToolsProperties()).collect(beanFactory);

        assertEquals(1, executors.size());
        assertEquals("getWeather", executors.get(0).getDefinition().getName());
    }

    @Test
    void collectsToolCallbackProviderBeans() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("provider",
                new StaticToolCallbackProvider(ToolCallbacks.from(new EmailTools())[0]));

        List<SpringAiToolCallbackExecutor> executors =
                new SpringAiToolCollector(new SpringAiToolsProperties()).collect(beanFactory);

        assertEquals(1, executors.size());
        assertEquals("sendEmail", executors.get(0).getDefinition().getName());
    }

    @Test
    void deduplicatesSameNameFromDifferentSources() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        // 同一工具名：直接 Bean + @Tool Bean 双来源，应去重为 1
        beanFactory.registerSingleton("weatherCallback", ToolCallbacks.from(new WeatherTools())[0]);
        beanFactory.registerSingleton("weatherTools", new WeatherTools());

        List<SpringAiToolCallbackExecutor> executors =
                new SpringAiToolCollector(new SpringAiToolsProperties()).collect(beanFactory);

        assertEquals(1, executors.size());
        assertEquals("getWeather", executors.get(0).getDefinition().getName());
    }

    @Test
    void scanToolAnnotatedBeansCanBeDisabled() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("weatherTools", new WeatherTools());

        SpringAiToolsProperties props = new SpringAiToolsProperties();
        props.setScanToolAnnotatedBeans(false);

        List<SpringAiToolCallbackExecutor> executors = new SpringAiToolCollector(props).collect(beanFactory);
        assertTrue(executors.isEmpty());
    }

    @Test
    void prefixIsAppliedToCollectedNames() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("weatherTools", new WeatherTools());

        SpringAiToolsProperties props = new SpringAiToolsProperties();
        props.setNamePrefix("sa_");

        List<SpringAiToolCallbackExecutor> executors = new SpringAiToolCollector(props).collect(beanFactory);
        assertEquals(1, executors.size());
        assertEquals("sa_getWeather", executors.get(0).getDefinition().getName());
    }
}