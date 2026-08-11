package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 鉴权配置。
 *
 * <pre>
 * type: none | bearerToken | basic | login
 *   bearerToken — 静态 Bearer token（token 可为 "${env.TOKEN}"）
 *   basic       — 静态 Basic 认证（username/password）
 *   login       — 先跑 login 请求提取变量（如 token），再按 injectHeader 模板注入请求头
 * applyTo       — 空/缺省 = 全部用例；指定用例名列表则仅对这些用例注入
 * retryOn401    — 收到 401 时重新登录（或刷新）后重试一次
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthConfig {

    private String type = "none";
    private String token;              // bearerToken 用
    private String username;           // basic 用
    private String password;           // basic 用
    private TestCaseConfig login;      // login 用：登录请求（含 extract）
    private Map<String, String> injectHeader = new HashMap<>();
    private List<String> applyTo;      // null/空 = 全部
    private boolean retryOn401 = false;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public TestCaseConfig getLogin() {
        return login;
    }

    public void setLogin(TestCaseConfig login) {
        this.login = login;
    }

    public Map<String, String> getInjectHeader() {
        return injectHeader;
    }

    public void setInjectHeader(Map<String, String> injectHeader) {
        this.injectHeader = injectHeader;
    }

    public List<String> getApplyTo() {
        return applyTo;
    }

    public void setApplyTo(List<String> applyTo) {
        this.applyTo = applyTo;
    }

    public boolean isRetryOn401() {
        return retryOn401;
    }

    public void setRetryOn401(boolean retryOn401) {
        this.retryOn401 = retryOn401;
    }
}
