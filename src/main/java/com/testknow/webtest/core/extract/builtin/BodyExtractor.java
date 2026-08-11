package com.testknow.webtest.core.extract.builtin;

import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.extract.Extractor;
import com.testknow.webtest.http.ResponseEnvelope;

import java.util.Map;
import java.util.Optional;

/**
 * 取整段响应体: {@code { name: raw, type: body }}。
 * 适用于整体作为凭据（如原始 JWT）的场景。
 */
public class BodyExtractor implements Extractor {

    private final String name;

    public BodyExtractor(Map<String, Object> spec) {
        this.name = String.valueOf(spec.get("name"));
    }

    @Override
    public Optional<String> extract(ResponseEnvelope resp, Variables vars) {
        String body = resp.body();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(body);
    }

    @Override
    public String describe() {
        return "响应体提取 → " + name;
    }
}
