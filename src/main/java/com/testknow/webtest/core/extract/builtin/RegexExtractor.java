package com.testknow.webtest.core.extract.builtin;

import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.extract.Extractor;
import com.testknow.webtest.http.ResponseEnvelope;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则提取: {@code { name: id, type: regex, pattern: "order-(\\d+)", group: 1 }}。
 * 匹配响应体，取指定捕获组（默认 1）。
 */
public class RegexExtractor implements Extractor {

    private final String name;
    private final Pattern pattern;
    private final int group;

    public RegexExtractor(Map<String, Object> spec) {
        this.name = String.valueOf(spec.get("name"));
        Object p = spec.get("pattern");
        if (p == null || String.valueOf(p).isBlank()) {
            throw new ConfigError("regex 提取器缺少 pattern 字段");
        }
        this.pattern = Pattern.compile(String.valueOf(p));
        Object g = spec.get("group");
        this.group = g == null ? 1 : ((Number) g).intValue();
    }

    @Override
    public Optional<String> extract(ResponseEnvelope resp, Variables vars) {
        String body = resp.body();
        if (body == null) {
            return Optional.empty();
        }
        Matcher m = pattern.matcher(body);
        if (m.find() && group <= m.groupCount()) {
            return Optional.ofNullable(m.group(group));
        }
        return Optional.empty();
    }

    @Override
    public String describe() {
        return "regex 提取 " + pattern.pattern() + " → " + name;
    }
}
