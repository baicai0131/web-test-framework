package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 被测站点配置。换网站 = 改这里（及 tests 的相对路径），代码零改动。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SiteConfig {

    private String name = "unnamed-site";
    private String baseUrl;
    private Map<String, String> globalHeaders = new HashMap<>();
    private Map<String, String> variables = new HashMap<>();
    private Timeouts timeouts = new Timeouts();
    private Retry retry = new Retry();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, String> getGlobalHeaders() {
        return globalHeaders;
    }

    public void setGlobalHeaders(Map<String, String> globalHeaders) {
        this.globalHeaders = globalHeaders;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }

    public Timeouts getTimeouts() {
        return timeouts;
    }

    public void setTimeouts(Timeouts timeouts) {
        this.timeouts = timeouts;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    /** 超时配置（毫秒）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Timeouts {
        private long connect = 3000;
        private long read = 10000;
        private long write = 10000;

        public long getConnect() {
            return connect;
        }

        public void setConnect(long connect) {
            this.connect = connect;
        }

        public long getRead() {
            return read;
        }

        public void setRead(long read) {
            this.read = read;
        }

        public long getWrite() {
            return write;
        }

        public void setWrite(long write) {
            this.write = write;
        }
    }

    /** 重试配置：仅对网络失败 / 5xx 重试，断言失败不重试。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Retry {
        private int count = 0;
        private long backoffMillis = 300;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public long getBackoffMillis() {
            return backoffMillis;
        }

        public void setBackoffMillis(long backoffMillis) {
            this.backoffMillis = backoffMillis;
        }
    }
}
