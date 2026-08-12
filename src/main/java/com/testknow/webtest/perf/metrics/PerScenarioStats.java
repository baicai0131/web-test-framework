package com.testknow.webtest.perf.metrics;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.util.concurrent.atomic.LongAdder;

/**
 * 单个性能场景的实时统计：请求数 / 错误数 / 延迟直方图 / 滑动窗口 TPS。
 * 全部无锁（LongAdder + wait-free Recorder），供压测热路径并发调用。
 */
public final class PerScenarioStats {

    private final LongAdder total = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final Recorder latencyRecorder;
    private final Histogram cumulative;   // 全部请求的累计直方图（主循环线程合并）
    private final SlidingWindowTps tps = new SlidingWindowTps();

    public PerScenarioStats() {
        // 1us ~ 10s 覆盖，2 位有效精度
        this.latencyRecorder = new Recorder(1_000, 10_000_000_000L, 2);
        this.cumulative = new Histogram(1_000, 10_000_000_000L, 2);
    }

    /** 记录一次请求结果（线程安全）。latencyNanos 为单次请求耗时。 */
    public void record(boolean ok, long latencyNanos, long nowNanos) {
        total.increment();
        if (!ok) {
            errors.increment();
        }
        latencyRecorder.recordValue(latencyNanos);
        tps.record(nowNanos);
    }

    /** 采样当前 TPS。 */
    public double sampleTps(long nowNanos) {
        return tps.sample(nowNanos);
    }

    public long total() {
        return total.sum();
    }

    public long errors() {
        return errors.sum();
    }

    public double errorRatePct() {
        long t = total();
        return t == 0 ? 0.0 : errors() * 100.0 / t;
    }

    /** 取窗口内直方图的区间快照（会重置记录窗口，供时间序列采样用）。 */
    public Histogram snapshotIntervalHistogram() {
        return latencyRecorder.getIntervalHistogram();
    }

    /** 将区间数据合并进累计直方图（防止采样重置导致聚合丢数据）。 */
    public void mergeIntoCumulative(Histogram interval) {
        if (interval.getTotalCount() > 0) {
            cumulative.add(interval);
        }
    }

    /** 全部请求的累计直方图快照（聚合最终指标用）。 */
    public Histogram cumulativeSnapshot() {
        return cumulative.copy();
    }
}
