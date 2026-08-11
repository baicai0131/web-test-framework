package com.testknow.webtest.core.auth;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigLoader;
import com.testknow.webtest.config.ConfigValidator;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.core.TestRunner;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.extract.ExtractorRegistry;
import com.testknow.webtest.core.result.ExecutionResult;
import com.testknow.webtest.http.HttpCaller;
import com.testknow.webtest.http.RequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthManagerTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tmp;

    private String baseUrl() {
        return "http://localhost:" + wm.getPort();
    }

    private AuthManager authManager(ProjectConfig cfg) {
        var site = cfg.getSite();
        HttpCaller caller = new HttpCaller(site);
        RequestBuilder rb = new RequestBuilder(site);
        return new AuthManager(cfg.getAuth(), caller, rb,
                new AssertionRegistry(), new ExtractorRegistry(), Variables.root(Map.of()));
    }

    @Test
    void bearerTokenStaticHeader() throws Exception {
        wm.stubFor(get("/x").willReturn(okJson("{}")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: x, path: /x }
                auth:
                  type: bearerToken
                  token: my-static-token
                """.formatted(baseUrl());
        ProjectConfig cfg = new ConfigLoader().load(write(yaml));
        new ConfigValidator(new AssertionRegistry()).validate(cfg);

        AuthManager auth = authManager(cfg);
        auth.login();
        assertEquals("Bearer my-static-token", auth.headers().get("Authorization"));
        assertTrue(auth.appliesTo("x"));
    }

    @Test
    void basicStaticHeader() throws Exception {
        wm.stubFor(get("/x").willReturn(okJson("{}")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: x, path: /x }
                auth:
                  type: basic
                  username: admin
                  password: secret
                """.formatted(baseUrl());
        ProjectConfig cfg = new ConfigLoader().load(write(yaml));
        new ConfigValidator(new AssertionRegistry()).validate(cfg);

        AuthManager auth = authManager(cfg);
        auth.login();
        // Base64(admin:secret) = YWRtaW46c2VjcmV0
        assertEquals("Basic YWRtaW46c2VjcmV0", auth.headers().get("Authorization"));
    }

    @Test
    void loginFlowExtractsTokenAndInjectsHeader() throws Exception {
        wm.stubFor(post("/login").willReturn(okJson("{\"data\":{\"access_token\":\"tok-abc\"}}")));
        wm.stubFor(get("/protected").willReturn(okJson("{\"ok\":true}")));

        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: p, path: /protected }
                auth:
                  type: login
                  login:
                    method: POST
                    path: /login
                    contentType: json
                    body: { u: "u", p: "p" }
                    extract:
                      - { name: token, type: jsonpath, expr: "$.data.access_token" }
                  injectHeader:
                    Authorization: "Bearer ${token}"
                """.formatted(baseUrl());
        ProjectConfig cfg = new ConfigLoader().load(write(yaml));
        new ConfigValidator(new AssertionRegistry()).validate(cfg);

        AuthManager auth = authManager(cfg);
        var setup = auth.login();
        assertTrue(setup.isPass());
        assertEquals("Bearer tok-abc", auth.headers().get("Authorization"));

        // 端到端：受保护接口应带上注入的 Authorization 头
        ExecutionResult result = new TestRunner(cfg, null, tmp).run();
        assertTrue(result.isAllPass());
        wm.verify(getRequestedFor(urlEqualTo("/protected"))
                .withHeader("Authorization", equalTo("Bearer tok-abc")));
    }

    @Test
    void retryOn401ReloginOnce() throws Exception {
        wm.stubFor(post("/login").willReturn(okJson("{\"data\":{\"access_token\":\"tok-1\"}}")));
        wm.stubFor(get("/protected").inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("authed"));
        wm.stubFor(get("/protected").inScenario("retry")
                .whenScenarioStateIs("authed")
                .willReturn(okJson("{\"ok\":true}")));

        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: p, path: /protected, asserts: [ { type: status, expected: 200 } ] }
                auth:
                  type: login
                  login:
                    method: POST
                    path: /login
                    contentType: json
                    body: { u: "u", p: "p" }
                    extract:
                      - { name: token, type: jsonpath, expr: "$.data.access_token" }
                  injectHeader:
                    Authorization: "Bearer ${token}"
                  retryOn401: true
                """.formatted(baseUrl());
        ProjectConfig cfg = new ConfigLoader().load(write(yaml));
        new ConfigValidator(new AssertionRegistry()).validate(cfg);

        ExecutionResult result = new TestRunner(cfg, null, tmp).run();
        assertTrue(result.isAllPass());
        // 首次登录 + 401 后重新登录 = 2 次
        wm.verify(2, postRequestedFor(urlEqualTo("/login")));
    }

    private Path write(String yaml) throws Exception {
        Path f = tmp.resolve("auth-" + System.nanoTime() + ".yaml");
        Files.writeString(f, yaml);
        return f;
    }
}
