package com.testknow.webtest.perf.metrics;

import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

/**
 * 滑动窗口 TPS 计数器：固定桶数的环形数组，O(1) 记录与采样。
 * 记录时仅自增对应时间桶（LongAdder，无锁）；采样时只累加窗口内未过期的桶。
 */
public final class SlidingWindowTps {

    private static final int BUCKETS = 10;
    private static final long WINDOW_SECONDS = 5;
    private static final long BUCKET_NANOS = WINDOW_SECONDS * 1_000_000_000L / BUCKETS;

    private final LongAdder[] counts;

    public SlidingWindowTps() {
        counts = Stream.generate(LongAdder::new).limit(BUCKETS).toArray(LongAdder[]::new);
    }

    /** 记录一次请求（线程安全）。 */
    public void record(long nowNanos) {
        counts[bucketIndex(nowNanos)].increment();
    }

    /** 采样窗口内的 TPS（请求数 / 窗口秒数）。 */
    public double sample(long nowNanos) {
        long cutoff = nowNanos - BUCKET_NANOS * BUCKETS;
        long total = 0;
        for (int i = 0; i < BUCKETS; i++) {
            long ts = (nowNanos / BUCKET_NANOS - (BUCKETS - 1) + i) * BUCKET_NANOS;
            if (ts >= cutoff) {
                total += counts[i].sum();
            }
        }
        return total / (double) WINDOW_SECONDS;
    }

    private static int bucketIndex(long nowNanos) {
        return (int) ((nowNanos / BUCKET_NANOS) % BUCKETS);
    }
}
