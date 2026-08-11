package com.testknow.webtest.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Headers;

import java.io.IOException;
import java.util.Optional;

/**
 * 响应封装：状态码 / 响应头 / 响应体 / 耗时，JSON 懒解析并缓存。
 * 断言与报告统一消费该模型。
 */
public class ResponseEnvelope {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int code;
    private final Headers headers;
    private final String body;
    private final long elapsedNanos;

    private JsonNode jsonCache;
    private boolean jsonParsed;

    public ResponseEnvelope(int code, Headers headers, String body, long elapsedNanos) {
        this.code = code;
        this.headers = headers == null ? Headers.of() : headers;
        this.body = body;
        this.elapsedNanos = elapsedNanos;
    }

    public int code() {
        return code;
    }

    public Headers headers() {
        return headers;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public String body() {
        return body;
    }

    /**
     * 响应体解析为 JSON 树（懒解析）。非 JSON 内容返回 empty。
     */
    public Optional<JsonNode> json() {
        if (jsonParsed) {
            return Optional.ofNullable(jsonCache);
        }
        jsonParsed = true;
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            jsonCache = MAPPER.readTree(body);
        } catch (IOException e) {
            jsonCache = null;
        }
        return Optional.ofNullable(jsonCache);
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public long elapsedMillis() {
        return elapsedNanos / 1_000_000;
    }
}
