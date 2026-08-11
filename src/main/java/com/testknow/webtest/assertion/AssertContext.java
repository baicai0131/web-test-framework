package com.testknow.webtest.assertion;

import com.testknow.webtest.core.PlaceholderResolver;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.http.ResponseEnvelope;

/**
 * 一次请求的断言上下文：响应 + 变量作用域 + 耗时。
 * 断言期望值若含占位符，可在求值时通过 {@link #resolve} 解析。
 */
public class AssertContext {

    private final ResponseEnvelope resp;
    private final Variables vars;
    private final long elapsedNanos;

    public AssertContext(ResponseEnvelope resp, Variables vars) {
        this.resp = resp;
        this.vars = vars == null ? Variables.empty() : vars;
        this.elapsedNanos = resp.elapsedNanos();
    }

    public ResponseEnvelope resp() {
        return resp;
    }

    public Variables vars() {
        return vars;
    }

    public long elapsedNanos() {
        return elapsedNanos;
    }

    public long elapsedMillis() {
        return elapsedNanos / 1_000_000;
    }

    /** 对模板串做占位符解析（用于断言期望值中的变量引用）。 */
    public String resolve(String template) {
        return PlaceholderResolver.INSTANCE.resolve(template, vars);
    }
}
