package com.testknow.webtest.assertion.builtin;

import com.testknow.webtest.assertion.AssertContext;
import com.testknow.webtest.assertion.Assertion;
import com.testknow.webtest.assertion.AssertionFailure;
import com.testknow.webtest.config.ConfigError;

import java.util.Map;
import java.util.Optional;

/**
 * 响应头断言:
 * <pre>
 * { type: header, name: Content-Type, contains: "application/json" }
 * { type: header, name: Server,       equals: "nginx" }
 * </pre>
 */
public class HeaderAssertion implements Assertion {

    public enum Op { EQUALS, CONTAINS }

    private final String name;
    private final Op op;
    private final String expected;

    public HeaderAssertion(Map<String, Object> spec) {
        Object n = spec.get("name");
        if (n == null || String.valueOf(n).isBlank()) {
            throw new ConfigError("header 断言缺少 name 字段");
        }
        this.name = String.valueOf(n);
        if (spec.containsKey("equals")) {
            this.op = Op.EQUALS;
            this.expected = String.valueOf(spec.get("equals"));
        } else if (spec.containsKey("contains")) {
            this.op = Op.CONTAINS;
            this.expected = String.valueOf(spec.get("contains"));
        } else {
            throw new ConfigError("header 断言需指定 equals/contains 之一");
        }
    }

    @Override
    public Optional<AssertionFailure> evaluate(AssertContext ctx) {
        String expectedEff = expected.contains("${") ? ctx.resolve(expected) : expected;
        String actual = ctx.resp().header(name);
        boolean ok;
        if (op == Op.EQUALS) {
            ok = expectedEff.equals(actual);
        } else {
            ok = actual != null && actual.contains(expectedEff);
        }
        if (ok) {
            return Optional.empty();
        }
        return Optional.of(new AssertionFailure("header",
                op.name().toLowerCase() + " \"" + expectedEff + "\"",
                actual == null ? "(缺失)" : actual, name));
    }

    @Override
    public String describe() {
        return "响应头 " + name + " " + op.name().toLowerCase() + " \"" + expected + "\"";
    }
}
