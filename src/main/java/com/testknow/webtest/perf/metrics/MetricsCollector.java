package com.testknow.webtest.perf.metrics;

import org.HdrHistogram.Histogram;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 性能指标采集器：按场景名聚合实时统计，每 tick 采样时间序列点，结束时聚合最终指标。
 */
public final class MetricsCollector {

    private final Map<String, PerScenarioStats> byScenario = new ConcurrentHashMap<>();
    private final List<TimeSeriesPoint> series = new java.util.concurrent.CopyOnWriteArrayList<>();

    private volatile long startedEpochSecond;

    public void start(long epochSecond) {
        this.startedEpochSecond = epochSecond;
    }

    public PerScenarioStats forScenario(String scenarioName) {
        return byScenario.computeIfAbsent(scenarioName, k -> new PerScenarioStats());
    }

    /** 记录一次请求（线程安全）。 */
    public void record(String scenarioName, boolean ok, long latencyNanos) {
        forScenario(scenarioName).record(ok, latencyNanos, System.nanoTime());
    }

    /** 每秒调用一次：为每个场景产出一个时间序列采样点（区间直方图并入累计）。 */
    public List<TimeSeriesPoint> sample(long epochSecond) {
        List<TimeSeriesPoint> points = new java.util.ArrayList<>();
        for (Map.Entry<String, PerScenarioStats> e : byScenario.entrySet()) {
            PerScenarioStats stats = e.getValue();
            long nowNanos = System.nanoTime();
            double tps = stats.sampleTps(nowNanos);
            Histogram interval = stats.snapshotIntervalHistogram();
            stats.mergeIntoCumulative(interval);
            double avg = interval.getTotalCount() == 0 ? 0
                    : interval.getMean() / 1_000_000.0;
            double p95 = interval.getTotalCount() == 0 ? 0
                    : interval.getValueAtPercentile(95) / 1_000_000.0;
            points.add(new TimeSeriesPoint(epochSecond, tps, avg, p95, stats.errorRatePct()));
        }
        series.addAll(points);
        return points;
    }

    /** 结束时聚合所有场景的最终指标（累计直方图，不丢数据）。 */
    public Map<String, PerfAggregate> aggregate(long elapsedNanos) {
        double durationSec = elapsedNanos / 1_000_000_000.0;
        Map<String, PerfAggregate> out = new ConcurrentHashMap<>();
        for (Map.Entry<String, PerScenarioStats> e : byScenario.entrySet()) {
            PerScenarioStats stats = e.getValue();
            Histogram hist = stats.cumulativeSnapshot();
            double tps = durationSec <= 0 ? 0 : stats.total() / durationSec;
            out.put(e.getKey(), PerfAggregate.from(
                    e.getKey(), hist, stats.total(), stats.errors(), tps, durationSec));
        }
        return out;
    }

    public List<TimeSeriesPoint> series() {
        return series;
    }

    public Map<String, PerScenarioStats> raw() {
        return byScenario.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
