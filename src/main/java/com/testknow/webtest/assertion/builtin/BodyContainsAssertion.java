package com.testknow.webtest.assertion.builtin;

import com.testknow.webtest.assertion.AssertContext;
import com.testknow.webtest.assertion.Assertion;
import com.testknow.webtest.assertion.AssertionFailure;
import com.testknow.webtest.config.ConfigError;

import java.util.Map;
import java.util.Optional;

/**
 * 响应体包含文本断言: {@code { type: bodyContains, text: "success" }}。
 */
public class BodyContainsAssertion implements Assertion {

    private final String text;

    public BodyContainsAssertion(Map<String, Object> spec) {
        Object t = spec.get("text");
        if (t == null) {
            throw new ConfigError("bodyContains 断言缺少 text 字段");
        }
        this.text = String.valueOf(t);
    }

    @Override
    public Optional<AssertionFailure> evaluate(AssertContext ctx) {
        String textEff = text.contains("${") ? ctx.resolve(text) : text;
        String body = ctx.resp().body();
        if (body != null && body.contains(textEff)) {
            return Optional.empty();
        }
        return Optional.of(new AssertionFailure("bodyContains",
                "包含 \"" + textEff + "\"", body == null ? "null" : "不包含", null));
    }

    @Override
    public String describe() {
        return "响应体包含 \"" + text + "\"";
    }
}
