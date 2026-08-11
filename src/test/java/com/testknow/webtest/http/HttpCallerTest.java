package com.testknow.webtest.http;

import com.testknow.webtest.config.model.SiteConfig;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;

class HttpCallerTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private String baseUrl() {
        return "http://localhost:" + wm.getPort();
    }

    private SiteConfig siteConfig(int retryCount) {
        SiteConfig s = new SiteConfig();
        s.setBaseUrl(baseUrl());
        SiteConfig.Retry r = new SiteConfig.Retry();
        r.setCount(retryCount);
        r.setBackoffMillis(20);
        s.setRetry(r);
        return s;
    }

    private Request getRequest(String path) {
        return new Request.Builder().url(baseUrl() + path).get().build();
    }

    @Test
    void sendsMethodAndReadsBody() throws Exception {
        wm.stubFor(get("/ping")
                .willReturn(ok("pong").withHeader("Content-Type", "text/plain")));
        ResponseEnvelope resp = new HttpCaller(siteConfig(0)).execute(getRequest("/ping"));
        assertEquals(200, resp.code());
        assertEquals("pong", resp.body());
        assertEquals("text/plain", resp.header("Content-Type"));
    }

    @Test
    void retries5xxThenReturnsLastResponse() throws Exception {
        wm.stubFor(get("/flaky").willReturn(aResponse().withStatus(503).withBody("busy")));
        ResponseEnvelope resp = new HttpCaller(siteConfig(2)).execute(getRequest("/flaky"));
        assertEquals(503, resp.code());
        wm.verify(3, getRequestedFor(urlEqualTo("/flaky"))); // 1 + 2 次重试
    }

    @Test
    void noRetryOn4xx() throws Exception {
        wm.stubFor(get("/nope").willReturn(aResponse().withStatus(404)));
        ResponseEnvelope resp = new HttpCaller(siteConfig(2)).execute(getRequest("/nope"));
        assertEquals(404, resp.code());
        wm.verify(1, getRequestedFor(urlEqualTo("/nope")));
    }

    @Test
    void connectionRefusedProducesIoError() throws Exception {
        // 动态取一个刚释放的端口，避免硬编码端口被本机服务占用
        int closedPort;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0)) {
            closedPort = ss.getLocalPort();
        }

        SiteConfig s = new SiteConfig();
        s.setBaseUrl("http://127.0.0.1:" + closedPort);
        SiteConfig.Retry r = new SiteConfig.Retry();
        r.setCount(0);
        s.setRetry(r);
        SiteConfig.Timeouts t = new SiteConfig.Timeouts();
        t.setConnect(1500);
        s.setTimeouts(t);
        HttpCaller caller = new HttpCaller(s);
        Request req = new Request.Builder().url("http://127.0.0.1:" + closedPort + "/x").get().build();
        try {
            caller.execute(req);
            org.junit.jupiter.api.Assertions.fail("应抛出 IOException");
        } catch (java.io.IOException expected) {
            assertTrue(true);
        }
    }
}
