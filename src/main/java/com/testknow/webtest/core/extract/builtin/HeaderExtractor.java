package com.testknow.webtest.core.extract.builtin;

import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.extract.Extractor;
import com.testknow.webtest.http.ResponseEnvelope;

import java.util.Map;
import java.util.Optional;

/**
 * 响应头提取: {@code { name: csrf, type: header, header: "X-CSRF-Token" }}。
 */
public class HeaderExtractor implements Extractor {

    private final String name;
    private final String headerName;

    public HeaderExtractor(Map<String, Object> spec) {
        this.name = String.valueOf(spec.get("name"));
        Object h = spec.get("header");
        if (h == null || String.valueOf(h).isBlank()) {
            throw new ConfigError("header 提取器缺少 header 字段");
        }
        this.headerName = String.valueOf(h);
    }

    @Override
    public Optional<String> extract(ResponseEnvelope resp, Variables vars) {
        return Optional.ofNullable(resp.header(headerName));
    }

    @Override
    public String describe() {
        return "响应头提取 " + headerName + " → " + name;
    }
}
