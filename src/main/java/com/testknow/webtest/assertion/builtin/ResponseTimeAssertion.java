package com.testknow.webtest.assertion.builtin;

import com.testknow.webtest.assertion.AssertContext;
import com.testknow.webtest.assertion.Assertion;
import com.testknow.webtest.assertion.AssertionFailure;
import com.testknow.webtest.config.ConfigError;

import java.util.Map;
import java.util.Optional;

/**
 * 响应时间断言（毫秒）: {@code { type: rtMs, max: 3000 }}。
 */
public class ResponseTimeAssertion implements Assertion {

    private final long maxMillis;

    public ResponseTimeAssertion(Map<String, Object> spec) {
        Object max = spec.get("max");
        if (max == null) {
            throw new ConfigError("rtMs 断言缺少 max 字段");
        }
        this.maxMillis = ((Number) max).longValue();
    }

    @Override
    public Optional<AssertionFailure> evaluate(AssertContext ctx) {
        long actual = ctx.elapsedMillis();
        if (actual <= maxMillis) {
            return Optional.empty();
        }
        return Optional.of(new AssertionFailure("rtMs",
                "<= " + maxMillis + "ms", actual + "ms", null));
    }

    @Override
    public String describe() {
        return "响应时间 <= " + maxMillis + "ms";
    }
}
