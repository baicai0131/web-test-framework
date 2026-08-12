package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 性能阈值门禁：任一不满足 → 该性能场景判定失败（CI 退出码 2）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerfThresholdsConfig {

    private Double errorRateMaxPct;   // 错误率上限（%），如 1.0 表示 1%
    private Double p95MsMax;          // P95 响应时间上限（毫秒）
    private Double p99MsMax;          // P99 响应时间上限（毫秒）
    private Double tpsMin;            // TPS 下限
    private Long totalRequestsMin;    // 总请求数下限

    public Double getErrorRateMaxPct() {
        return errorRateMaxPct;
    }

    public void setErrorRateMaxPct(Double errorRateMaxPct) {
        this.errorRateMaxPct = errorRateMaxPct;
    }

    public Double getP95MsMax() {
        return p95MsMax;
    }

    public void setP95MsMax(Double p95MsMax) {
        this.p95MsMax = p95MsMax;
    }

    public Double getP99MsMax() {
        return p99MsMax;
    }

    public void setP99MsMax(Double p99MsMax) {
        this.p99MsMax = p99MsMax;
    }

    public Double getTpsMin() {
        return tpsMin;
    }

    public void setTpsMin(Double tpsMin) {
        this.tpsMin = tpsMin;
    }

    public Long getTotalRequestsMin() {
        return totalRequestsMin;
    }

    public void setTotalRequestsMin(Long totalRequestsMin) {
        this.totalRequestsMin = totalRequestsMin;
    }

    public boolean isEmpty() {
        return errorRateMaxPct == null && p95MsMax == null && p99MsMax == null
                && tpsMin == null && totalRequestsMin == null;
    }
}
