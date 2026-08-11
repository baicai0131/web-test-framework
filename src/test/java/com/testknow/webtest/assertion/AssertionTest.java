package com.testknow.webtest.assertion;

import com.testknow.webtest.assertion.builtin.BodyContainsAssertion;
import com.testknow.webtest.assertion.builtin.HeaderAssertion;
import com.testknow.webtest.assertion.builtin.JsonPathAssertion;
import com.testknow.webtest.assertion.builtin.ResponseTimeAssertion;
import com.testknow.webtest.assertion.builtin.StatusAssertion;
import com.testknow.webtest.http.ResponseEnvelope;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionTest {

    private static ResponseEnvelope resp(int code, String headerValue, String body, long elapsedNanos) {
        Headers headers = headerValue == null
                ? Headers.of()
                : Headers.of("Content-Type", headerValue);
        return new ResponseEnvelope(code, headers, body, elapsedNanos);
    }

    private static AssertContext ctx(ResponseEnvelope r) {
        return new AssertContext(r, com.testknow.webtest.core.Variables.root(Map.of()));
    }

    @Test
    void statusPassAndFail() {
        assertTrue(new StatusAssertion(Map.of("expected", 200))
                .evaluate(ctx(resp(200, null, "", 0))).isEmpty());
        Optional<AssertionFailure> f = new StatusAssertion(Map.of("expected", 201))
                .evaluate(ctx(resp(200, null, "", 0)));
        assertTrue(f.isPresent());
        assertEquals("201", f.get().expected());
        assertEquals("200", f.get().actual());
    }

    @Test
    void jsonPathEquals() {
        String body = "{\"data\":{\"token\":\"abc\"}}";
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.data.token", "equals", "abc"))
                .evaluate(ctx(resp(200, null, body, 0))).isEmpty());
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.data.token", "equals", "xyz"))
                .evaluate(ctx(resp(200, null, body, 0))).isPresent());
    }

    @Test
    void jsonPathExistsAndNotEmpty() {
        String body = "{\"a\":1,\"list\":[\"x\"],\"blank\":\"\"}";
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.a", "exists", true))
                .evaluate(ctx(resp(200, null, body, 0))).isEmpty());
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.missing", "exists", true))
                .evaluate(ctx(resp(200, null, body, 0))).isPresent());
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.list", "notEmpty", true))
                .evaluate(ctx(resp(200, null, body, 0))).isEmpty());
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.blank", "notEmpty", true))
                .evaluate(ctx(resp(200, null, body, 0))).isPresent());
    }

    @Test
    void jsonPathContains() {
        String body = "{\"roles\":[\"USER\",\"ADMIN\"],\"msg\":\"hello world\"}";
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.roles", "contains", "ADMIN"))
                .evaluate(ctx(resp(200, null, body, 0))).isEmpty());
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.msg", "contains", "world"))
                .evaluate(ctx(resp(200, null, body, 0))).isEmpty());
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.roles", "contains", "ROOT"))
                .evaluate(ctx(resp(200, null, body, 0))).isPresent());
    }

    @Test
    void jsonPathOnNonJsonBodyFailsCleanly() {
        String body = "not json at all";
        assertTrue(new JsonPathAssertion(Map.of("expr", "$.a", "exists", true))
                .evaluate(ctx(resp(200, null, body, 0))).isPresent());
    }

    @Test
    void responseTimePassAndFail() {
        assertTrue(new ResponseTimeAssertion(Map.of("max", 100))
                .evaluate(ctx(resp(200, null, "", 5_000_000))).isEmpty());
        assertTrue(new ResponseTimeAssertion(Map.of("max", 1))
                .evaluate(ctx(resp(200, null, "", 5_000_000))).isPresent());
    }

    @Test
    void bodyContains() {
        assertTrue(new BodyContainsAssertion(Map.of("text", "success"))
                .evaluate(ctx(resp(200, null, "{\"status\":\"success\"}", 0))).isEmpty());
        assertTrue(new BodyContainsAssertion(Map.of("text", "boom"))
                .evaluate(ctx(resp(200, null, "{\"status\":\"success\"}", 0))).isPresent());
    }

    @Test
    void headerEqualsAndContains() {
        assertTrue(new HeaderAssertion(Map.of("name", "Content-Type", "contains", "application/json"))
                .evaluate(ctx(resp(200, "application/json; charset=utf-8", "", 0))).isEmpty());
        assertTrue(new HeaderAssertion(Map.of("name", "Content-Type", "equals", "application/json"))
                .evaluate(ctx(resp(200, "application/json; charset=utf-8", "", 0))).isPresent());
        assertTrue(new HeaderAssertion(Map.of("name", "X-Missing", "contains", "x"))
                .evaluate(ctx(resp(200, null, "", 0))).isPresent());
    }

    @Test
    void unknownTypeRejectedByRegistry() {
        AssertionRegistry registry = new AssertionRegistry();
        assertTrue(registry.parse(Map.of("type", "status", "expected", 200)) instanceof StatusAssertion);
        org.junit.jupiter.api.Assertions.assertThrows(
                com.testknow.webtest.config.ConfigError.class,
                () -> registry.parse(Map.of("type", "nope")));
    }
}
