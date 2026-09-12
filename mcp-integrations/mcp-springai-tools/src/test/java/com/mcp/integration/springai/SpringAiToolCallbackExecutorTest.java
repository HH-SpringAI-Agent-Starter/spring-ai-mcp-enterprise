package com.mcp.integration.springai;

import com.mcp.enterprise.core.model.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring AI 工具执行器适配器测试（V1.23）
 *
 * <p>使用真实 Spring AI {@code ToolCallbacks.from()} 把 {@code @Tool} 方法转成
 * {@code MethodToolCallback}，验证定义转换与调用桥接链路。</p>
 */
class SpringAiToolCallbackExecutorTest {

    /** 测试用 @Tool Bean（与真实业务写法一致） */
    static class WeatherTools {

        @Tool(name = "getWeather", description = "查询指定城市的实时天气")
        public String getWeather(@ToolParam(description = "城市名，如：北京") String city,
                                 @ToolParam(required = false, description = "温度单位：celsius/fahrenheit") String unit) {
            String resolvedUnit = (unit == null || unit.isBlank()) ? "celsius" : unit;
            return "{\"city\":\"" + city + "\",\"unit\":\"" + resolvedUnit + "\",\"temp\":22}";
        }
    }

    private SpringAiToolCallbackExecutor newExecutor() {
        return new SpringAiToolCallbackExecutor(
                ToolCallbacks.from(new WeatherTools())[0], new SpringAiToolsProperties());
    }

    @Test
    void definitionIsBuiltFromToolCallback() {
        SpringAiToolCallbackExecutor executor = newExecutor();
        ToolDefinition def = executor.getDefinition();

        assertEquals("getWeather", def.getName());
        assertEquals("查询指定城市的实时天气", def.getDescription());
        assertEquals("springai", def.getCategory());
        assertEquals("1.0", def.getVersion());
        assertEquals("admin,user", def.getRequiredRoles());
        assertTrue(def.isEnabled());
        assertEquals("spring-ai", def.getModule());

        // 输入 Schema 应被解析为对象结构
        assertNotNull(def.getInputSchema());
        assertEquals("object", def.getInputSchema().get("type"));
        assertNotNull(def.getInputSchema().get("properties"));
    }

    @Test
    void executeInvokesToolAndReturnsParsedResult() {
        SpringAiToolCallbackExecutor executor = newExecutor();
        Map<String, Object> result = executor.execute(Map.of("city", "上海")).block();

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("getWeather", result.get("tool"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) result.get("result");
        assertEquals("上海", inner.get("city"));
        assertEquals("celsius", inner.get("unit"));
        assertEquals(22, ((Number) inner.get("temp")).intValue());
    }

    @Test
    void executePropagatesOptionalParams() {
        SpringAiToolCallbackExecutor executor = newExecutor();
        Map<String, Object> result = executor.execute(Map.of("city", "北京", "unit", "fahrenheit")).block();

        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.get("success"));
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) result.get("result");
        assertEquals("fahrenheit", inner.get("unit"));
    }

    @Test
    void executeHandlesBadInputWithoutThrowing() {
        SpringAiToolCallbackExecutor executor = newExecutor();
        // 缺少必填参数 city，Spring AI 侧会抛异常，适配器必须兜底返回失败结构而不是抛出去
        Map<String, Object> result = executor.execute(Map.of()).block();

        assertNotNull(result);
        assertTrue(result.containsKey("success"));
        assertEquals("getWeather", result.get("tool"));
    }

    @Test
    void namePrefixIsApplied() {
        SpringAiToolsProperties props = new SpringAiToolsProperties();
        props.setNamePrefix("sa_");
        SpringAiToolCallbackExecutor executor =
                new SpringAiToolCallbackExecutor(ToolCallbacks.from(new WeatherTools())[0], props);
        assertEquals("sa_getWeather", executor.getDefinition().getName());
    }

    @Test
    void nullParamsAreTolerated() {
        SpringAiToolCallbackExecutor executor = newExecutor();
        Map<String, Object> result = executor.execute(null).block();
        assertNotNull(result);
    }
}