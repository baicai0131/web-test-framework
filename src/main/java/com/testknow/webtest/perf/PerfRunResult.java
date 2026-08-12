package com.testknow.webtest.perf;

import com.testknow.webtest.perf.metrics.PerfAggregate;
import com.testknow.webtest.perf.metrics.TimeSeriesPoint;

import java.util.List;
import java.util.Map;

/**
 * 一次压测的结果：每个场景的聚合指标 + 每秒时间序列。
 */
public record PerfRunResult(
        String planName,
        long startedNanos,
        long elapsedNanos,
        Map<String, PerfAggregate> aggregates,
        List<TimeSeriesPoint> series
) {
    public long elapsedMillis() {
        return elapsedNanos / 1_000_000;
    }
}
