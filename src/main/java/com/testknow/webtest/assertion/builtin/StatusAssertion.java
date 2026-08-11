package com.testknow.webtest.assertion.builtin;

import com.testknow.webtest.assertion.AssertContext;
import com.testknow.webtest.assertion.Assertion;
import com.testknow.webtest.assertion.AssertionFailure;
import com.testknow.webtest.config.ConfigError;

import java.util.Map;
import java.util.Optional;

/**
 * 状态码断言: {@code { type: status, expected: 200 }}。
 */
public class StatusAssertion implements Assertion {

    private final int expected;

    public StatusAssertion(Map<String, Object> spec) {
        Object exp = spec.get("expected");
        if (exp == null) {
            throw new ConfigError("status 断言缺少 expected 字段");
        }
        this.expected = ((Number) exp).intValue();
    }

    @Override
    public Optional<AssertionFailure> evaluate(AssertContext ctx) {
        int actual = ctx.resp().code();
        if (actual == expected) {
            return Optional.empty();
        }
        return Optional.of(new AssertionFailure("status", String.valueOf(expected), String.valueOf(actual), null));
    }

    @Override
    public String describe() {
        return "HTTP 状态码 = " + expected;
    }
}
