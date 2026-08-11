package com.testknow.webtest.core;

import com.testknow.webtest.config.ConfigError;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符插值引擎（全框架横切能力）。
 *
 * <pre>
 * ${variables.X}   从变量作用域解析（快捷写法 ${X} 等同）
 * ${env.X}         从 OS 环境变量 / 系统属性解析（CI Secret 用此方式注入，不落盘）
 * ${random.uuid}   UUID
 * ${timestamp.iso} ISO-8601 时间戳
 * ${now.epochMilli} 当前毫秒
 * </pre>
 *
 * 解析顺序：内置函数 → env → 作用域变量。未解析的占位符保持字面量原样返回，
 * 便于在报告中看到哪个变量缺失；超过深度上限视为循环引用并抛 {@link ConfigError}。
 * 无状态、线程安全。
 */
public final class PlaceholderResolver {

    public static final PlaceholderResolver INSTANCE = new PlaceholderResolver();

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");
    private static final int MAX_DEPTH = 10;

    private PlaceholderResolver() {
    }

    public String resolve(String template, Variables vars) {
        return resolve(template, vars, 0);
    }

    private String resolve(String template, Variables vars, int depth) {
        if (template == null || !template.contains("${")) {
            return template;
        }
        if (depth > MAX_DEPTH) {
            throw new ConfigError("变量解析深度超限（疑似循环引用）: " + template);
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1).trim();
            m.appendReplacement(sb, Matcher.quoteReplacement(resolveExpr(expr, vars, depth)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String resolveExpr(String expr, Variables vars, int depth) {
        switch (expr) {
            case "random.uuid" -> {
                return UUID.randomUUID().toString();
            }
            case "timestamp.iso" -> {
                return Instant.now().toString();
            }
            case "now.epochMilli" -> {
                return String.valueOf(System.currentTimeMillis());
            }
            default -> {
                // 继续
            }
        }
        if (expr.startsWith("env.")) {
            String name = expr.substring(4);
            String v = System.getenv(name);
            if (v == null) {
                v = System.getProperty(name);
            }
            if (v == null) {
                return "${" + expr + "}"; // 保持字面量
            }
            return v;
        }
        String key = expr;
        if (key.startsWith("variables.")) {
            key = key.substring("variables.".length());
        }
        if (vars != null) {
            String v = vars.get(key);
            if (v != null) {
                // 变量值本身可能仍含占位符（如 `${variables.B}` 引用了含 `${env.X}` 的 B）
                return v.contains("${") ? resolve(v, vars, depth + 1) : v;
            }
        }
        return "${" + expr + "}"; // 未找到：保持字面量
    }
}
