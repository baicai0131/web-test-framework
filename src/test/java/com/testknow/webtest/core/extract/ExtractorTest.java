package com.testknow.webtest.core.extract;

import com.testknow.webtest.core.Variables;
import com.testknow.webtest.http.ResponseEnvelope;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractorTest {

    private final ExtractorRegistry registry = new ExtractorRegistry();

    private static ResponseEnvelope resp(String body) {
        return new ResponseEnvelope(200, Headers.of("X-Token", "csrf-abc"), body, 0);
    }

    private Optional<String> extract(Map<String, Object> spec) {
        return registry.parse(spec).extract(resp("{\"data\":{\"token\":\"tok-1\"},\"items\":[\"a\",\"b\"],\"id\":\"order-42\"}"),
                Variables.empty());
    }

    @Test
    void jsonpathExtractsScalar() {
        assertEquals(Optional.of("tok-1"),
                extract(Map.of("name", "token", "type", "jsonpath", "expr", "$.data.token")));
    }

    @Test
    void jsonpathExtractListTakesFirst() {
        assertEquals(Optional.of("a"),
                extract(Map.of("name", "first", "type", "jsonpath", "expr", "$.items")));
    }

    @Test
    void jsonpathMissingReturnsEmpty() {
        assertTrue(extract(Map.of("name", "x", "type", "jsonpath", "expr", "$.missing")).isEmpty());
    }

    @Test
    void jsonpathExprSupportsVariable() {
        Variables vars = Variables.root(Map.of("P", "$.data.token"));
        Optional<String> v = registry.parse(Map.of("name", "t", "type", "jsonpath", "expr", "${variables.P}"))
                .extract(resp("{\"data\":{\"token\":\"tok-9\"}}"), vars);
        assertEquals(Optional.of("tok-9"), v);
    }

    @Test
    void regexExtractsGroup() {
        Optional<String> v = registry.parse(Map.of("name", "id", "type", "regex", "pattern", "order-(\\d+)"))
                .extract(resp("{\"id\":\"order-42\"}"), Variables.empty());
        assertEquals(Optional.of("42"), v);
    }

    @Test
    void headerExtracts() {
        Optional<String> v = registry.parse(Map.of("name", "csrf", "type", "header", "header", "X-Token"))
                .extract(resp(""), Variables.empty());
        assertEquals(Optional.of("csrf-abc"), v);
    }

    @Test
    void bodyExtractsWholeBody() {
        Optional<String> v = registry.parse(Map.of("name", "raw", "type", "body"))
                .extract(resp("{\"a\":1}"), Variables.empty());
        assertEquals(Optional.of("{\"a\":1}"), v);
    }
}
