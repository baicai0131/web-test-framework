package com.testknow.webtest.core;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.ConfigLoader;
import com.testknow.webtest.config.ConfigValidator;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.core.result.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 环境切换 + 变量插值 + 配置错误 的端到端测试。
 */
class EnvironmentFlowTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tmp;

    private String yaml() {
        return """
                env: test
                environments:
                  test:
                    baseUrl: %s
                    variables: { ENV_TAG: "test" }
                  prod:
                    baseUrl: %s
                    variables: { ENV_TAG: "prod" }
                site:
                  name: env-demo
                  baseUrl: %s
                  variables: { SHARED: "s1" }
                tests:
                  - name: echo-env
                    method: GET
                    path: /echo
                    query: { tag: "${variables.ENV_TAG}", shared: "${variables.SHARED}" }
                    asserts:
                      - { type: status, expected: 200 }
                      - { type: jsonpath, expr: "$.args.tag", equals: "${variables.ENV_TAG}" }
                """.formatted("http://localhost:" + wm.getPort(),
                "http://localhost:" + wm.getPort(),
                "http://localhost:" + wm.getPort());
    }

    @Test
    void environmentVariablesFlowIntoRequestAndAssertions() throws Exception {
        wm.stubFor(get(urlPathEqualTo("/echo")).willReturn(okJson("{\"args\":{\"tag\":\"test\",\"shared\":\"s1\"}}")));
        Path cfgFile = tmp.resolve("env.yaml");
        Files.writeString(cfgFile, yaml());

        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        new ConfigValidator(new AssertionRegistry()).validate(cfg);
        ExecutionResult result = new TestRunner(cfg, null, tmp).run();
        assertTrue(result.isAllPass());
        assertEquals("test", result.getEnvironmentName());
    }

    @Test
    void envFlagOverridesDefault() throws Exception {
        wm.stubFor(get(urlPathEqualTo("/echo")).willReturn(okJson("{\"args\":{\"tag\":\"prod\",\"shared\":\"s1\"}}")));
        Path cfgFile = tmp.resolve("env.yaml");
        Files.writeString(cfgFile, yaml());

        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        new ConfigValidator(new AssertionRegistry()).validate(cfg);
        ExecutionResult result = new TestRunner(cfg, "prod", tmp).run();
        assertTrue(result.isAllPass());
        assertEquals("prod", result.getEnvironmentName());
    }

    @Test
    void unknownEnvNameThrows() throws Exception {
        Path cfgFile = tmp.resolve("env.yaml");
        Files.writeString(cfgFile, yaml());
        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        new ConfigValidator(new AssertionRegistry()).validate(cfg);
        ConfigError e = assertThrows(ConfigError.class, () -> new TestRunner(cfg, "nonexist", tmp).run());
        assertTrue(e.getMessage().contains("未定义环境"));
    }

    @Test
    void unknownAuthTypeRejectedByValidator() throws Exception {
        String bad = """
                site:
                  baseUrl: %s
                tests:
                  - { name: x, path: /x }
                auth:
                  type: magicAuth
                """.formatted("http://localhost:" + wm.getPort());
        Path cfgFile = tmp.resolve("bad-auth.yaml");
        Files.writeString(cfgFile, bad);
        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        ConfigError e = assertThrows(ConfigError.class,
                () -> new ConfigValidator(new AssertionRegistry()).validate(cfg));
        assertTrue(e.getMessage().contains("未知 auth.type"));
    }

    @Test
    void dataSetReferencingMissingTestRejected() throws Exception {
        String bad = """
                site:
                  baseUrl: %s
                tests:
                  - { name: x, path: /x }
                dataSets:
                  - { test: nope, file: f.csv }
                """.formatted("http://localhost:" + wm.getPort());
        Path cfgFile = tmp.resolve("bad-ds.yaml");
        Files.writeString(cfgFile, bad);
        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        ConfigError e = assertThrows(ConfigError.class,
                () -> new ConfigValidator(new AssertionRegistry()).validate(cfg));
        assertTrue(e.getMessage().contains("引用的用例不存在"));
    }
}
