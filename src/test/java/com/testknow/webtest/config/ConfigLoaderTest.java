package com.testknow.webtest.config;

import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.model.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path tmp;

    private Path writeYaml(String content) throws Exception {
        Path f = tmp.resolve("test.yaml");
        Files.writeString(f, content);
        return f;
    }

    private void validate(ProjectConfig cfg) {
        new ConfigValidator(new AssertionRegistry()).validate(cfg);
    }

    @Test
    void loadsValidConfig() throws Exception {
        Path f = writeYaml("""
                site:
                  name: demo
                  baseUrl: https://httpbin.org
                tests:
                  - name: ping
                    path: /get
                    asserts:
                      - { type: status, expected: 200 }
                """);
        ProjectConfig cfg = new ConfigLoader().load(f);
        assertEquals("demo", cfg.getSite().getName());
        assertEquals("https://httpbin.org", cfg.getSite().getBaseUrl());
        assertEquals(1, cfg.getTests().size());
        assertEquals("ping", cfg.getTests().get(0).getName());
        assertEquals("GET", cfg.getTests().get(0).getMethod());
    }

    @Test
    void missingFileThrowsConfigError() {
        ConfigError e = assertThrows(ConfigError.class,
                () -> new ConfigLoader().load(Path.of("nope.yaml")));
        assertTrue(e.getMessage().contains("不存在"));
    }

    @Test
    void invalidYamlThrowsConfigError() throws Exception {
        Path f = writeYaml("site: [unclosed");
        ConfigError e = assertThrows(ConfigError.class, () -> new ConfigLoader().load(f));
        assertTrue(e.getMessage().contains("YAML"));
    }

    @Test
    void unknownAssertionTypeFailsValidation() throws Exception {
        Path f = writeYaml("""
                site:
                  baseUrl: http://localhost:1
                tests:
                  - name: t
                    path: /
                    asserts:
                      - { type: bogusType, x: 1 }
                """);
        ProjectConfig cfg = new ConfigLoader().load(f);
        ConfigError e = assertThrows(ConfigError.class, () -> validate(cfg));
        assertTrue(e.getMessage().contains("未知断言类型"));
    }

    @Test
    void missingBaseUrlFailsValidation() throws Exception {
        Path f = writeYaml("""
                tests:
                  - name: t
                    path: /
                """);
        ProjectConfig cfg = new ConfigLoader().load(f);
        assertThrows(ConfigError.class, () -> validate(cfg));
    }

    @Test
    void emptyTestsFailsValidation() throws Exception {
        Path f = writeYaml("site:\n  baseUrl: http://localhost:1");
        ProjectConfig cfg = new ConfigLoader().load(f);
        assertThrows(ConfigError.class, () -> validate(cfg));
    }

    @Test
    void badMethodFailsValidation() throws Exception {
        Path f = writeYaml("""
                site:
                  baseUrl: http://localhost:1
                tests:
                  - name: t
                    method: FLY
                    path: /
                """);
        ProjectConfig cfg = new ConfigLoader().load(f);
        assertThrows(ConfigError.class, () -> validate(cfg));
    }
}
