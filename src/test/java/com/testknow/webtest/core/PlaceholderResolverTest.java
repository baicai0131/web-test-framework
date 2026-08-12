package com.testknow.webtest.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderResolverTest {

    private final PlaceholderResolver resolver = PlaceholderResolver.INSTANCE;

    @Test
    void resolvesScopedVariables() {
        Variables vars = Variables.root(Map.of("ACCOUNT", "demo@test.io"))
                .child(Map.of("token", "tok-123"));
        assertEquals("demo@test.io", resolver.resolve("${variables.ACCOUNT}", vars));
        assertEquals("tok-123", resolver.resolve("${token}", vars));
    }

    @Test
    void envVariableResolvesFromSystem() {
        String v = resolver.resolve("${env.PATH}", Variables.empty());
        assertTrue(v != null && !v.contains("${"));
    }

    @Test
    void builtinRandomUuidProducesDistinctValues() {
        String a = resolver.resolve("${random.uuid}", Variables.empty());
        String b = resolver.resolve("${random.uuid}", Variables.empty());
        assertNotEquals(a, b);
        assertTrue(a.length() == 36);
    }

    @Test
    void missingVariableKeepsLiteral() {
        assertEquals("${variables.NOPE}", resolver.resolve("${variables.NOPE}", Variables.empty()));
    }

    @Test
    void interpolatesInsideLargerTemplate() {
        Variables vars = Variables.root(Map.of("tag", "v1"));
        assertEquals("/api/v1/items", resolver.resolve("/api/${tag}/items", vars));
    }

    @Test
    void nestedVariableResolution() {
        // B 的值引用 A；A 直接有值
        Variables vars = Variables.root(Map.of("A", "hello", "B", "${variables.A}-suffix"));
        assertEquals("hello-suffix", resolver.resolve("${variables.B}", vars));
    }

    @Test
    void cycleDetectionThrows() {
        // 配置变量自引用：TestRunner.resolveVariables 负责检测
        org.junit.jupiter.api.Assertions.assertThrows(
                com.testknow.webtest.config.ConfigError.class,
                () -> TestRuntime.resolveVariables(Map.of("A", "${variables.A}")));
    }
}
