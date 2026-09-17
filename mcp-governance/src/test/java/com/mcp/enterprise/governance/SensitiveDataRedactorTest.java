package com.mcp.enterprise.governance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.26 敏感数据脱敏器测试：邮箱 / 密钥键值 / 身份证 / 银行卡 / 手机号 / 递归结构。
 */
class SensitiveDataRedactorTest {

    private SensitiveDataRedactor redactor;

    @BeforeEach
    void setUp() {
        redactor = new SensitiveDataRedactor(new McpGovernanceProperties());
    }

    @Test
    @DisplayName("邮箱脱敏")
    void redactsEmail() {
        assertThat(redactor.redact("contact alice@corp.com now"))
                .isEqualTo("contact *** now");
    }

    @Test
    @DisplayName("密钥类键值脱敏（apiKey=/secret=/password=/token=）")
    void redactsSecrets() {
        assertThat(redactor.redact("apiKey=sk-abcdefgh12345678"))
                .isEqualTo("apiKey=***");
        assertThat(redactor.redact("password: hunter2secret"))
                .isEqualTo("password: ***");
    }

    @Test
    @DisplayName("身份证 / 银行卡 / 手机号脱敏")
    void redactsPii() {
        assertThat(redactor.redact("id 110101199003078888 ok"))
                .isEqualTo("id *** ok");
        assertThat(redactor.redact("card 6222020200112233445"))
                .isEqualTo("card ***");
        assertThat(redactor.redact("call 13800138000 please"))
                .isEqualTo("call *** please");
    }

    @Test
    @DisplayName("递归脱敏 Map/List 且不改动原对象")
    void redactsNestedStructures() {
        Map<String, Object> original = new java.util.LinkedHashMap<>();
        original.put("email", "a@b.com");
        original.put("items", List.of("phone: 13912345678", "plain"));
        original.put("nested", Map.of("token", "tok-abcdef123456"));

        Object out = redactor.redactValue(original);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) out;
        assertThat(result.get("email")).isEqualTo("***");
        assertThat(result.get("items")).isEqualTo(List.of("phone: ***", "plain"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) result.get("nested");
        assertThat(nested.get("token")).isEqualTo("***");

        // 原对象未被改动
        assertThat(original.get("email")).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("关闭脱敏时原样返回")
    void disabledReturnsOriginal() {
        McpGovernanceProperties props = new McpGovernanceProperties();
        props.getRedaction().setEnabled(false);
        SensitiveDataRedactor off = new SensitiveDataRedactor(props);

        assertThat(off.redact("email a@b.com")).isEqualTo("email a@b.com");
        assertThat(off.isEnabled()).isFalse();
        assertThat(off.ruleNames()).isEmpty();
    }

    @Test
    @DisplayName("空值安全")
    void nullSafe() {
        assertThat(redactor.redact(null)).isNull();
        assertThat(redactor.redact("")).isEmpty();
        assertThat(redactor.redactMap(null)).isEmpty();
        assertThat(redactor.redactValue(42)).isEqualTo(42);
    }
}