package com.testknow.webtest.perf.metrics;

import org.HdrHistogram.Histogram;

/**
 * 一个性能场景聚合后的最终指标（P50/P90/P95/P99/P999 等）。
 * 由 {@link Histogram} 统计得出。
 */
public record PerfAggregate(
        String scenarioName,
        long totalRequests,
        long errorCount,
        double errorRatePct,
        double tpsMean,
        double avgRtMs,
        double p50Ms,
        double p90Ms,
        double p95Ms,
        double p99Ms,
        double p999Ms
) {
    public static PerfAggregate from(String name, Histogram hist, long total, long errors,
                                     double tpsMean, double durationSec) {
        return new PerfAggregate(
                name,
                total,
                errors,
                total == 0 ? 0.0 : errors * 100.0 / total,
                tpsMean,
                total == 0 ? 0.0 : hist.getMean() / 1_000_000.0,
                hist.getValueAtPercentile(50) / 1_000_000.0,
                hist.getValueAtPercentile(90) / 1_000_000.0,
                hist.getValueAtPercentile(95) / 1_000_000.0,
                hist.getValueAtPercentile(99) / 1_000_000.0,
                hist.getValueAtPercentile(99.9) / 1_000_000.0);
    }
}
