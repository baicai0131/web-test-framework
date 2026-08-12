package com.testknow.webtest.perf.metrics;

/**
 * 每秒一个采样点（用于报告曲线）。
 */
public record TimeSeriesPoint(
        long epochSecond,   // 相对开始经过的秒数
        double tps,
        double avgRtMs,
        double p95RtMs,
        double errorRatePct
) {
}
