package com.testknow.webtest.http;

import com.testknow.webtest.config.model.SiteConfig;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OkHttp 封装：连接池、超时、重试（仅对网络失败 / 5xx，断言失败不重试）。
 * 全局单客户端实例，连接池跨请求复用——为后续性能引擎复用打基础。
 */
public class HttpCaller {

    private static final Logger log = LoggerFactory.getLogger(HttpCaller.class);

    private final OkHttpClient client;
    private final int retryCount;
    private final long backoffMillis;

    public HttpCaller(SiteConfig site) {
        SiteConfig.Timeouts t = site.getTimeouts();
        SiteConfig.Retry r = site.getRetry();
        this.retryCount = Math.max(0, r == null ? 0 : r.getCount());
        this.backoffMillis = Math.max(0, r == null ? 0 : r.getBackoffMillis());

        this.client = new OkHttpClient.Builder()
                .connectTimeout(t.getConnect(), TimeUnit.MILLISECONDS)
                .readTimeout(t.getRead(), TimeUnit.MILLISECONDS)
                .writeTimeout(t.getWrite(), TimeUnit.MILLISECONDS)
                .connectionPool(new okhttp3.ConnectionPool(50, 1, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .addInterceptor((chain) -> {
                    Request req = chain.request();
                    long start = System.nanoTime();
                    // 注意：不能在此关闭响应，否则下游读取响应体时抛 "closed"
                    Response resp = chain.proceed(req);
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    log.info("[{}] {} {} -> {} ({}ms)",
                            req.method(), req.url(), resp.code(), ms);
                    return resp;
                })
                .build();
    }

    /**
     * 执行请求并返回封装后的响应。失败重试后返回最后一次结果（由断言判定成败）。
     *
     * @throws IOException 重试耗尽后仍为网络错误
     */
    public ResponseEnvelope execute(Request request) throws IOException {
        ResponseEnvelope last = null;
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            long start = System.nanoTime();
            try (Response resp = client.newCall(request).execute()) {
                String body = resp.body() == null ? "" : resp.body().string();
                last = new ResponseEnvelope(resp.code(), resp.headers(), body, System.nanoTime() - start);
                if (attempt > 0) {
                    log.warn("重试第 {}/{} 次成功: {} {}", attempt, retryCount, request.method(), request.url());
                }
                if (last.code() < 500) {
                    return last; // 4xx/3xx/2xx 直接返回
                }
                log.warn("收到 {} 状态码，第 {}/{} 次重试: {} {}", last.code(), attempt, retryCount, request.method(), request.url());
            } catch (IOException e) {
                if (attempt < retryCount) {
                    log.warn("网络异常，第 {}/{} 次重试: {} {}", attempt + 1, retryCount, request.method(), request.url(), e);
                } else {
                    throw e;
                }
            }
            if (attempt < retryCount) {
                sleepQuietly(backoffMillis * (attempt + 1));
            }
        }
        if (last == null) {
            throw new IOException("请求未产生任何响应: " + request.url());
        }
        return last;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
