package com.testknow.webtest.assertion.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.testknow.webtest.assertion.AssertContext;
import com.testknow.webtest.assertion.Assertion;
import com.testknow.webtest.assertion.AssertionFailure;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.util.JsonPaths;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSONPath 断言:
 * <pre>
 * { type: jsonpath, expr: "$.data.token", equals: "abc" }
 * { type: jsonpath, expr: "$.list",      contains: "item1" }
 * { type: jsonpath, expr: "$.a",         exists: true }
 * { type: jsonpath, expr: "$.b",         notEmpty: true }
 * </pre>
 * 编译后的 JsonPath 缓存复用（线程安全，为性能测试热路径准备）。
 */
public class JsonPathAssertion implements Assertion {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Configuration CONF = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    public enum Op { EQUALS, CONTAINS, EXISTS, NOT_EMPTY }

    private final String path;
    private final JsonPath compiled;
    private final Op op;
    private final Object expected;

    public JsonPathAssertion(Map<String, Object> spec) {
        Object expr = spec.get("expr");
        if (expr == null || String.valueOf(expr).isBlank()) {
            throw new ConfigError("jsonpath 断言缺少 expr 字段");
        }
        this.path = String.valueOf(expr);
        this.compiled = JsonPaths.compile(path);

        Op detected = detectOp(spec);
        this.op = detected;
        this.expected = spec.get(opKey(detected));
        if (expected == null && op != Op.EXISTS && op != Op.NOT_EMPTY) {
            throw new ConfigError("jsonpath 断言需要断言值字段: " + opKey(detected));
        }
    }

    private static Op detectOp(Map<String, Object> spec) {
        if (spec.containsKey("equals")) return Op.EQUALS;
        if (spec.containsKey("contains")) return Op.CONTAINS;
        if (spec.containsKey("exists")) return Op.EXISTS;
        if (spec.containsKey("notEmpty")) return Op.NOT_EMPTY;
        throw new ConfigError("jsonpath 断言需指定 equals/contains/exists/notEmpty 之一");
    }

    private static String opKey(Op op) {
        return switch (op) {
            case EQUALS -> "equals";
            case CONTAINS -> "contains";
            case EXISTS -> "exists";
            case NOT_EMPTY -> "notEmpty";
        };
    }

    @Override
    public Optional<AssertionFailure> evaluate(AssertContext ctx) {
        Object expectedEff = resolveExpected(ctx);
        Object actual = read(ctx);
        boolean ok;
        switch (op) {
            case EQUALS -> ok = actual != null && render(actual).equals(render(expectedEff));
            case CONTAINS -> ok = contains(actual, expectedEff);
            case EXISTS -> ok = actual != null;
            case NOT_EMPTY -> ok = isNotEmpty(actual);
            default -> ok = false;
        }
        if (ok) {
            return Optional.empty();
        }
        return Optional.of(new AssertionFailure("jsonpath",
                opKey(op) + " " + render(expectedEff), render(actual), path));
    }

    /** 期望值为字符串且含占位符时，在求值时按当前变量作用域解析。 */
    private Object resolveExpected(AssertContext ctx) {
        if (expected instanceof String s && s.contains("${")) {
            return ctx.resolve(s);
        }
        return expected;
    }

    private Object read(AssertContext ctx) {
        String body = ctx.resp().body();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return compiled.read(body, CONF);
        } catch (PathNotFoundException e) {
            return null;
        } catch (RuntimeException e) {
            return "!解析失败: " + e.getMessage();
        }
    }

    private static boolean contains(Object actual, Object expected) {
        if (actual == null) return false;
        if (actual instanceof List<?> list) {
            return list.stream().anyMatch(item -> render(item).equals(render(expected)));
        }
        if (actual instanceof String s) {
            return s.contains(render(expected));
        }
        return false;
    }

    private static boolean isNotEmpty(Object actual) {
        if (actual == null) return false;
        if (actual instanceof String s) return !s.isBlank();
        if (actual instanceof List<?> l) return !l.isEmpty();
        if (actual instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    private static String render(Object o) {
        if (o == null) return "null";
        if (o instanceof String s) return s;
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    @Override
    public String describe() {
        return "JSONPath " + path + " " + opKey(op) + " " + render(expected);
    }
}
