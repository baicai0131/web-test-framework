package com.testknow.webtest.perf;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.testknow.webtest.config.ConfigLoader;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.perf.metrics.PerfAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 性能引擎端到端测试：并发压测、时长/迭代停止、TPS/直方图指标、阈值门禁。
 */
class LoadGeneratorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tmp;

    private String baseUrl() {
        return "http://localhost:" + wm.getPort();
    }

    private Path writeConfig(String yaml) throws Exception {
        Path f = tmp.resolve("perf-" + System.nanoTime() + ".yaml");
        Files.writeString(f, yaml);
        return f;
    }

    private List<PerfRunOutcome> runPerf(String yaml) throws Exception {
        ProjectConfig cfg = new ConfigLoader().load(writeConfig(yaml));
        return new PerfRunner(cfg, null, null).run();
    }

    @Test
    void durationBasedRunsAndProducesMetrics() throws Exception {
        wm.stubFor(get("/ping").willReturn(ok("pong")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: ping, path: /ping, asserts: [ { type: status, expected: 200 } ] }
                performance:
                  - name: smoke
                    durationSec: 2
                    scenarios:
                      - { ref: ping, users: 10, thinkTimeMs: 20 }
                """.formatted(baseUrl());

        List<PerfRunOutcome> outcomes = runPerf(yaml);
        assertEquals(1, outcomes.size());
        PerfRunOutcome outcome = outcomes.get(0);
        assertTrue(outcome.isPass(), "无阈值配置应默认通过");

        PerfRunResult result = outcome.runResult();
        assertTrue(result.elapsedMillis() >= 1800, "至少运行约 2 秒, 实际 " + result.elapsedMillis() + "ms");

        PerfAggregate agg = result.aggregates().get("ping");
        assertNotNull(agg, "应产出场景 ping 的聚合指标");
        assertTrue(agg.totalRequests() > 10, "10 用户 × 2 秒应有足够请求, 实际 " + agg.totalRequests());
        assertTrue(agg.tpsMean() > 0, "TPS 应 > 0");
        // 直方图分位数单调性
        assertTrue(agg.p50Ms() <= agg.p95Ms() && agg.p95Ms() <= agg.p99Ms());
        // 高并发启动瞬间允许偶发连接失败；错误率统计准确性由 errorRateCountedFrom5xx 单独验证
        assertTrue(agg.errorRatePct() < 2.0, "错误率应接近 0, 实际 " + agg.errorRatePct() + "%");
    }

    @Test
    void iterationBasedStopsPerUser() throws Exception {
        wm.stubFor(get("/ping").willReturn(ok("pong")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: ping, path: /ping }
                performance:
                  - name: iterations
                    iterations: 5
                    scenarios:
                      - { ref: ping, users: 4 }
                """.formatted(baseUrl());

        List<PerfRunOutcome> outcomes = runPerf(yaml);
        PerfAggregate agg = outcomes.get(0).runResult().aggregates().get("ping");
        assertNotNull(agg);
        // 4 用户 × 5 迭代 = 20 请求
        assertEquals(20, agg.totalRequests(), "迭代模式每个用户精确执行迭代次数");
    }

    @Test
    void errorRateCountedFrom5xx() throws Exception {
        wm.stubFor(get("/flaky").willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(503)));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: flaky, path: /flaky }
                performance:
                  - name: err
                    durationSec: 2
                    scenarios:
                      - { ref: flaky, users: 5, thinkTimeMs: 50 }
                """.formatted(baseUrl());

        List<PerfRunOutcome> outcomes = runPerf(yaml);
        PerfAggregate agg = outcomes.get(0).runResult().aggregates().get("flaky");
        assertTrue(agg.errorRatePct() == 100.0, "全部 503 错误率应为 100%, 实际 " + agg.errorRatePct() + "%");
    }

    @Test
    void thresholdViolationFailsOutcome() throws Exception {
        wm.stubFor(get("/ping").willReturn(ok("pong")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: ping, path: /ping }
                performance:
                  - name: strict
                    durationSec: 1
                    scenarios:
                      - { ref: ping, users: 2 }
                    thresholds:
                      tpsMin: 999999
                """.formatted(baseUrl());

        List<PerfRunOutcome> outcomes = runPerf(yaml);
        assertTrue(!outcomes.get(0).isPass(), "TPS 阈值不可能达到，应判定失败");
        assertEquals(1, outcomes.get(0).violations().size());
        assertTrue(outcomes.get(0).violations().get(0).metric().equals("TPS"));
    }

    @Test
    void thresholdPassWhenMet() throws Exception {
        wm.stubFor(get("/ping").willReturn(ok("pong")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: ping, path: /ping }
                performance:
                  - name: loose
                    durationSec: 1
                    scenarios:
                      - { ref: ping, users: 5 }
                    thresholds:
                      errorRateMaxPct: 50
                      totalRequestsMin: 1
                """.formatted(baseUrl());

        List<PerfRunOutcome> outcomes = runPerf(yaml);
        assertTrue(outcomes.get(0).isPass(), "宽松阈值应通过");
    }

    @Test
    void perVuSessionIsolated_loginPerUser() throws Exception {
        // 登录接口返回带 token 的 JSON；受保护接口校验 Authorization 头
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
                performance:
                  - name: vu
                    iterations: 3
                    scenarios:
                      - { ref: p, users: 4 }
                """.formatted(baseUrl());

        List<PerfRunOutcome> outcomes = runPerf(yaml);
        PerfAggregate agg = outcomes.get(0).runResult().aggregates().get("p");
        assertNotNull(agg);
        // 4 用户 × 3 迭代 = 12 请求
        assertEquals(12, agg.totalRequests());

        // 每个虚拟用户独立登录一次 = 恰好 4 次登录（而非共享全局一次）
        wm.verify(4, postRequestedFor(urlEqualTo("/login")));
        // 受保护接口收到带 Bearer 头的请求
        wm.verify(getRequestedFor(urlEqualTo("/protected"))
                .withHeader("Authorization", equalTo("Bearer tok-abc")));
    }
}
