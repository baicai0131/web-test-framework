package com.testknow.webtest.core.extract.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.core.PlaceholderResolver;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.extract.Extractor;
import com.testknow.webtest.http.ResponseEnvelope;
import com.testknow.webtest.util.JsonPaths;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSONPath 提取: {@code { name: token, type: jsonpath, expr: "$.data.token" }}。
 * 表达式支持变量插值；列表取首元素；标量转字符串。
 */
public class JsonPathExtractor implements Extractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String expr;

    public JsonPathExtractor(Map<String, Object> spec) {
        this.name = String.valueOf(spec.get("name"));
        Object e = spec.get("expr");
        if (e == null || String.valueOf(e).isBlank()) {
            throw new ConfigError("jsonpath 提取器缺少 expr 字段");
        }
        this.expr = String.valueOf(e);
    }

    @Override
    public Optional<String> extract(ResponseEnvelope resp, Variables vars) {
        String resolved = PlaceholderResolver.INSTANCE.resolve(expr, vars);
        JsonPath path = JsonPaths.compile(resolved);
        String body = resp.body();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            Object value = path.read(body);
            return Optional.ofNullable(render(value));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String render(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : render(list.get(0));
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @Override
    public String describe() {
        return "jsonpath 提取 " + expr + " → " + name;
    }
}
