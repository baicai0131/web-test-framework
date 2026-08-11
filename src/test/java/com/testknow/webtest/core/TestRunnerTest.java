package com.testknow.webtest.core;

import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigLoader;
import com.testknow.webtest.config.ConfigValidator;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.core.result.CaseResult;
import com.testknow.webtest.core.result.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

/**
 * 端到端：YAML 配置 → 校验 → TestRunner → 汇总。后端用 WireMock mock。
 */
class TestRunnerTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tmp;

    @Test
    void runsYamlConfigAndCountsPassFail() throws Exception {
        wm.stubFor(get("/json").willReturn(okJson("{\"data\":{\"token\":\"abc\"}}")));
        wm.stubFor(post("/echo").willReturn(okJson("{\"received\":{\"name\":\"test\"}}")));
        // /missing 未 stub → WireMock 返回 404

        String yaml = """
                site:
                  name: local-mock
                  baseUrl: %s
                tests:
                  - name: get-json
                    path: /json
                    asserts:
                      - { type: status, expected: 200 }
                      - { type: jsonpath, expr: "$.data.token", equals: "abc" }
                  - name: missing
                    path: /missing
                    asserts:
                      - { type: status, expected: 200 }
                  - name: create
                    method: POST
                    path: /echo
                    contentType: json
                    body: { name: test }
                    asserts:
                      - { type: status, expected: 200 }
                      - { type: jsonpath, expr: "$.received.name", equals: "test" }
                """.formatted("http://localhost:" + wm.getPort());

        Path cfgFile = tmp.resolve("config.yaml");
        Files.writeString(cfgFile, yaml);

        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        new ConfigValidator(new AssertionRegistry()).validate(cfg);

        ExecutionResult result = new TestRunner(cfg, null, tmp).run();

        assertEquals(3, result.getTotal());
        assertEquals(2, result.getPassed());
        assertEquals(1, result.getFailed());
        assertFalse(result.isAllPass());

        CaseResult failed = result.getCases().get(1);
        assertEquals("missing", failed.getName());
        assertEquals(404, failed.getStatusCode());
        assertFalse(failed.isPass());
        assertEquals(1, failed.getFailures().size());
        assertEquals("status", failed.getFailures().get(0).check());
        assertTrue(failed.getFailures().get(0).render().contains("200"));
    }

    @Test
    void allPassWhenAssertionsMatch() throws Exception {
        wm.stubFor(get("/ok").willReturn(okJson("{\"a\":1}")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - name: ok
                    path: /ok
                    asserts:
                      - { type: status, expected: 200 }
                      - { type: jsonpath, expr: "$.a", equals: 1 }
                """.formatted("http://localhost:" + wm.getPort());

        Path cfgFile = tmp.resolve("ok.yaml");
        Files.writeString(cfgFile, yaml);
        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        new ConfigValidator(new AssertionRegistry()).validate(cfg);

        ExecutionResult result = new TestRunner(cfg, null, tmp).run();
        assertTrue(result.isAllPass());
        assertEquals(1, result.getPassed());
    }
}
