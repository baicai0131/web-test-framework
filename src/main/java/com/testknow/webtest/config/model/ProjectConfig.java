package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 顶层配置：site + tests。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectConfig {

    private SiteConfig site = new SiteConfig();
    private List<TestCaseConfig> tests = new ArrayList<>();
    private String env;                                 // 默认环境名（--env 可覆盖）
    private Map<String, EnvironmentConfig> environments = new LinkedHashMap<>();
    private AuthConfig auth = new AuthConfig();
    private List<DataSetConfig> dataSets = new ArrayList<>();
    private List<PerfConfig> performance = new ArrayList<>();

    public SiteConfig getSite() {
        return site;
    }

    public void setSite(SiteConfig site) {
        this.site = site;
    }

    public List<TestCaseConfig> getTests() {
        return tests;
    }

    public void setTests(List<TestCaseConfig> tests) {
        this.tests = tests;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public Map<String, EnvironmentConfig> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Map<String, EnvironmentConfig> environments) {
        this.environments = environments;
    }

    public AuthConfig getAuth() {
        return auth;
    }

    public void setAuth(AuthConfig auth) {
        this.auth = auth;
    }

    public List<DataSetConfig> getDataSets() {
        return dataSets;
    }

    public void setDataSets(List<DataSetConfig> dataSets) {
        this.dataSets = dataSets;
    }

    public List<PerfConfig> getPerformance() {
        return performance;
    }

    public void setPerformance(List<PerfConfig> performance) {
        this.performance = performance;
    }
}
