package com.mcp.enterprise.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * V1.26 敏感数据脱敏器（PII / Secret 就地脱敏）。
 *
 * <p>内置规则（按固定顺序执行，先身份证后银行卡避免 18 位重叠）：</p>
 * <ul>
 *   <li>邮箱（{@code a@b.c}）</li>
 *   <li>密钥类键值（{@code apiKey=/secret=/password=/token=}）</li>
 *   <li>中国大陆身份证（18 位）</li>
 *   <li>银行卡 / 信用卡（16–19 位连续数字）</li>
 *   <li>中国大陆手机号（{@code 1[3-9]xxxxxxxxx}）</li>
 * </ul>
 *
 * <p>{@link #redactValue(Object)} 可递归处理 {@code Map/List/String}，
 * 供审计链路在落库/出参前统一打码——对齐 Tyk MCP 网关采购清单中的
 * 「real-time PII/PHI redaction in transit」要求。</p>
 */
public class SensitiveDataRedactor {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataRedactor.class);

    /** 内置规则名称（有序，先长后短避免重叠误伤） */
    private static final String[] BUILTIN_RULES = {"email", "secret", "idcard", "bankcard", "phone"};

    private final Map<String, Pattern> patterns = new LinkedHashMap<>();
    private final String mask;
    private final boolean enabled;

    /** 密钥类键名（Map 条目整体打码）：apiKey / secret / password / token / credential 等 */
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i)^(api[_-]?key|secret|password|passwd|pwd|token|credential|authorization" +
            "|access[_-]?key|private[_-]?key|client[_-]?secret)$");

    public SensitiveDataRedactor(McpGovernanceProperties properties) {
        this.enabled = properties.getRedaction().isEnabled();
        this.mask = properties.getRedaction().getMask() == null ? "***" : properties.getRedaction().getMask();

        patterns.put("email", Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"));
        patterns.put("secret", Pattern.compile("(?i)((?:api[_-]?key|secret|password|token|credential)" +
                "\\s*[:=]\\s*)[\"']?[A-Za-z0-9_.\\-$]{8,}[\"']?"));
        patterns.put("idcard", Pattern.compile("\\b\\d{17}[\\dXx]\\b"));
        patterns.put("bankcard", Pattern.compile("\\b\\d{16,19}\\b"));
        patterns.put("phone", Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"));

        if (enabled) {
            log.info("🔐 [V1.26] 敏感数据脱敏器已启用（{} 条内置规则, mask={}）", patterns.size(), mask);
        } else {
            log.warn("⚠️ [V1.26] 敏感数据脱敏已关闭（redaction.enabled=false）——审计链路将保留原文，请确认合规要求");
        }
    }

    /** 是否激活。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 对单个字符串脱敏。 */
    public String redact(String input) {
        if (!enabled || input == null || input.isEmpty()) {
            return input;
        }
        String out = input;
        // 密钥类键值：保留键名与分隔符，仅打码值（apiKey=sk-xxx → apiKey=***）
        out = patterns.get("secret").matcher(out)
                .replaceAll(m -> m.group(1) + mask);
        for (Map.Entry<String, Pattern> e : patterns.entrySet()) {
            if ("secret".equals(e.getKey())) {
                continue;
            }
            out = e.getValue().matcher(out).replaceAll(mask);
        }
        return out;
    }

    /**
     * 递归脱敏：字符串直接打码；Map/List 逐元素处理（返回新对象，不改动入参）；
     * 其他类型原样返回。
     */
    @SuppressWarnings("unchecked")
    public Object redactValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return redact(s);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                // 密钥类键名 → 值整体打码（如 {"password": "hunter2"} → {"password": "***"}）
                if (e.getKey() instanceof String key && SECRET_KEY.matcher(key).matches()) {
                    out.put(e.getKey(), mask);
                    continue;
                }
                out.put(e.getKey(), redactValue(e.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(redactValue(item));
            }
            return out;
        }
        return value;
    }

    /** 便捷方法：对参数 Map 脱敏。 */
    public Map<String, Object> redactMap(Map<String, Object> args) {
        if (args == null) {
            return java.util.Map.of();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) redactValue(new LinkedHashMap<>(args));
        return out;
    }

    public List<String> ruleNames() {
        return enabled ? List.of(BUILTIN_RULES) : List.of();
    }
}