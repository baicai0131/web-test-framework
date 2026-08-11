package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 环境定义：可选覆盖 baseUrl，携带本环境专属变量。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnvironmentConfig {

    private String baseUrl;
    private Map<String, String> variables = new HashMap<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }
}
