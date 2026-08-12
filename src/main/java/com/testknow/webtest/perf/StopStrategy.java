package com.testknow.webtest.perf;

import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.model.PerfConfig;

/**
 * 压测停止策略：按时长（durationSec）或每个用户迭代次数（iterations）停止。
 */
public interface StopStrategy {

    boolean isExpired(long startedNanos);

    static StopStrategy of(PerfConfig config) {
        if (config.usesDuration()) {
            return new DurationBased(config.getDurationSec());
        }
        if (config.getIterations() != null && config.getIterations() > 0) {
            return new IterationBased(config.getIterations());
        }
        throw new ConfigError("性能配置 '" + config.getName() + "' 需要 durationSec 或 iterations");
    }

    /** 按总时长（秒）停止。 */
    final class DurationBased implements StopStrategy {
        private final long durationNanos;

        public DurationBased(int durationSec) {
            this.durationNanos = durationSec * 1_000_000_000L;
        }

        @Override
        public boolean isExpired(long startedNanos) {
            return System.nanoTime() - startedNanos >= durationNanos;
        }
    }

    /** 按每个用户迭代次数停止。 */
    final class IterationBased implements StopStrategy {
        private final int iterations;

        public IterationBased(int iterations) {
            this.iterations = iterations;
        }

        @Override
        public boolean isExpired(long startedNanos) {
            return false; // 由 Worker 自行按迭代数结束，主循环等全部 worker 完成
        }

        public int iterations() {
            return iterations;
        }
    }
}
