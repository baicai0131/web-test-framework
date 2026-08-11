package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个测试用例配置。path 为相对路径（不含主机），由 site.baseUrl 拼接。
 * asserts 保持原始 YAML 结构，由断言注册表在加载期校验、执行期解析。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCaseConfig {

    private String name;
    private String method = "GET";
    private String path;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> query = new HashMap<>();
    private Object body;                    // Map=JSON 对象 / String=原始文本 / null=无 body
    private String contentType;
    private List<Map<String, Object>> asserts = new ArrayList<>();
    private List<Map<String, Object>> extract = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method == null ? "GET" : method.toUpperCase();
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getQuery() {
        return query;
    }

    public void setQuery(Map<String, String> query) {
        this.query = query;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public List<Map<String, Object>> getAsserts() {
        return asserts;
    }

    public void setAsserts(List<Map<String, Object>> asserts) {
        this.asserts = asserts;
    }

    public List<Map<String, Object>> getExtract() {
        return extract;
    }

    public void setExtract(List<Map<String, Object>> extract) {
        this.extract = extract;
    }
}
