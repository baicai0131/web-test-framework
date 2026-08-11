package com.testknow.webtest.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.model.SiteConfig;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.PlaceholderResolver;
import com.testknow.webtest.core.Variables;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由 {@link TestCaseConfig} 构建 OkHttp {@link Request}。
 * path / query / headers / body 均支持 {@code ${...}} 插值；
 * 鉴权头最后注入（优先级最高）。
 */
public class RequestBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");
    private static final PlaceholderResolver RESOLVER = PlaceholderResolver.INSTANCE;

    private final SiteConfig site;

    public RequestBuilder(SiteConfig site) {
        this.site = site;
    }

    public Request build(TestCaseConfig tc, Variables vars, Map<String, String> authHeaders) {
        Variables vs = vars == null ? Variables.empty() : vars;

        String baseUrl = site.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ConfigError("site.baseUrl 未配置");
        }
        String path = RESOLVER.resolve(tc.getPath(), vs);

        HttpUrl url = HttpUrl.parse(baseUrl + path);
        if (url == null) {
            throw new ConfigError("无法解析 URL: " + baseUrl + path);
        }
        if (tc.getQuery() != null && !tc.getQuery().isEmpty()) {
            HttpUrl.Builder ub = url.newBuilder();
            tc.getQuery().forEach((k, v) -> ub.addQueryParameter(k, RESOLVER.resolve(v, vs)));
            url = ub.build();
        }

        Request.Builder rb = new Request.Builder().url(url).method(tc.getMethod(), buildBody(tc, vs));

        site.getGlobalHeaders().forEach((k, v) -> rb.header(k, RESOLVER.resolve(v, vs)));
        if (tc.getHeaders() != null) {
            tc.getHeaders().forEach((k, v) -> rb.header(k, RESOLVER.resolve(v, vs)));
        }
        if (authHeaders != null && !authHeaders.isEmpty()) {
            authHeaders.forEach(rb::header);
        }
        return rb.build();
    }

    private RequestBody buildBody(TestCaseConfig tc, Variables vars) {
        Object body = tc.getBody();
        if (body == null) {
            return null;
        }
        Object resolved = resolveObject(body, vars);
        MediaType mediaType;
        String contentType = tc.getContentType();
        if (contentType == null || contentType.isBlank()) {
            mediaType = JSON;
        } else {
            mediaType = MediaType.parse(contentType);
        }
        if (resolved instanceof Map<?, ?> || resolved instanceof List<?>) {
            try {
                return RequestBody.create(MAPPER.writeValueAsBytes(resolved),
                        mediaType == null ? JSON : mediaType);
            } catch (JsonProcessingException e) {
                throw new ConfigError("body 序列化失败: " + e.getMessage(), e);
            }
        }
        return RequestBody.create(String.valueOf(resolved), mediaType == null ? TEXT : mediaType);
    }

    /** 递归对字符串叶子做插值。 */
    private static Object resolveObject(Object o, Variables vars) {
        if (o instanceof String s) {
            return RESOLVER.resolve(s, vars);
        }
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), resolveObject(v, vars)));
            return out;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            l.forEach(v -> out.add(resolveObject(v, vars)));
            return out;
        }
        return o;
    }
}
