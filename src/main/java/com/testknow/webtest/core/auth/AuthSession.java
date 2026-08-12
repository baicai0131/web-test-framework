package com.testknow.webtest.core.auth;

import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.result.CaseResult;

import java.util.Map;

/**
 * 一次鉴权会话的产物：注入请求头的模板解析结果 + 本次会话变量作用域 + 登录结果（可选）。
 * 性能测试中每个虚拟用户持有一个独立的 AuthSession（各自登录、各自的 token）。
 */
public record AuthSession(Map<String, String> headers, Variables vars, CaseResult loginResult) {

    public static final AuthSession NONE = new AuthSession(Map.of(), Variables.empty(), null);

    /** 静态鉴权（bearer/basic）无登录请求，返回 null。 */
    public CaseResult extractedCaseResult() {
        return loginResult;
    }
}
