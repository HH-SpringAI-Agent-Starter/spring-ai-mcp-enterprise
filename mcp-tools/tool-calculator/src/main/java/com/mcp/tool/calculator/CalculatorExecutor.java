package com.mcp.tool.calculator;

import com.mcp.enterprise.core.model.ToolDefinition;
import com.mcp.enterprise.core.tool.McpToolExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * 数学计算器工具 (McpToolExecutor SPI 演示)
 *
 * 安全执行数学表达式计算，内置简易算术解析器，无需外部引擎依赖。
 */
@Component
public class CalculatorExecutor implements McpToolExecutor {

    private static final String SAFE_PATTERN = "^[0-9+\\-*/%.() \\t]+$";

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("expression", Map.of(
                "type", "string",
                "description", "数学表达式，如：1 + 2 * 3、100 / (5 + 5)、3.14 * 2^2"
        ));

        return new ToolDefinition(
                "calculator", "计算器", "安全执行数学表达式计算，支持加减乘除和括号运算", "demo",
                "1.0.0", null, true, "admin,user", 5000, 10,
                Map.of("type", "object", "properties", properties, "required", List.of("expression")), null
        );
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> params) {
        String expression = params != null && params.containsKey("expression")
                ? (String) params.get("expression")
                : "";

        if (expression == null || expression.isBlank()) {
            return Mono.just(Map.of("success", false, "error", "表达式不能为空"));
        }

        // 安全检查：只允许数字和基本运算符
        if (!expression.matches(SAFE_PATTERN)) {
            return Mono.just(Map.of("success", false, "error", "表达式包含非法字符，仅支持数字和 + - * / % . ()"));
        }

        return Mono.fromCallable(() -> {
            try {
                double result = evaluate(expression);
                String resultStr = result == Math.floor(result) && !Double.isInfinite(result)
                        ? String.valueOf((long) result) + ".0"
                        : String.valueOf(result);
                return Map.of(
                        "success", true,
                        "result", resultStr,
                        "expression", expression
                );
            } catch (Exception e) {
                return Map.of(
                        "success", false,
                        "error", "计算异常: " + e.getMessage()
                );
            }
        });
    }

    /**
     * 简易算术表达式求值器
     * 支持 +,-,*,/,%,(,) 和浮点数
     */
    private double evaluate(String expr) {
        // 去掉空格
        expr = expr.replaceAll("\\s+", "");

        // 转换为后缀表达式并求值
        return evalPostfix(toPostfix(expr));
    }

    private java.util.List<String> toPostfix(String infix) {
        java.util.List<String> output = new java.util.ArrayList<>();
        Stack<Character> ops = new Stack<>();
        StringBuilder num = new StringBuilder();

        for (int i = 0; i < infix.length(); i++) {
            char c = infix.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                num.append(c);
            } else {
                if (num.length() > 0) {
                    output.add(num.toString());
                    num.setLength(0);
                }
                if (c == '(') {
                    ops.push(c);
                } else if (c == ')') {
                    while (!ops.isEmpty() && ops.peek() != '(') {
                        output.add(String.valueOf(ops.pop()));
                    }
                    ops.pop(); // remove '('
                } else if ("+-*/%".indexOf(c) >= 0) {
                    int prec = (c == '+' || c == '-') ? 1 : 2;
                    while (!ops.isEmpty() && ops.peek() != '('
                            && ((ops.peek() == '+' || ops.peek() == '-') ? 1 : 2) >= prec) {
                        output.add(String.valueOf(ops.pop()));
                    }
                    ops.push(c);
                }
            }
        }
        if (num.length() > 0) {
            output.add(num.toString());
        }
        while (!ops.isEmpty()) {
            output.add(String.valueOf(ops.pop()));
        }
        return output;
    }

    private double evalPostfix(java.util.List<String> tokens) {
        Stack<Double> stack = new Stack<>();
        for (String token : tokens) {
            switch (token) {
                case "+" -> {
                    double b = stack.pop(), a = stack.pop();
                    stack.push(a + b);
                }
                case "-" -> {
                    double b = stack.pop(), a = stack.pop();
                    stack.push(a - b);
                }
                case "*" -> {
                    double b = stack.pop(), a = stack.pop();
                    stack.push(a * b);
                }
                case "/" -> {
                    double b = stack.pop(), a = stack.pop();
                    if (b == 0) throw new ArithmeticException("除数不能为0");
                    stack.push(a / b);
                }
                case "%" -> {
                    double b = stack.pop(), a = stack.pop();
                    stack.push(a % b);
                }
                default -> stack.push(Double.parseDouble(token));
            }
        }
        return stack.pop();
    }
}
