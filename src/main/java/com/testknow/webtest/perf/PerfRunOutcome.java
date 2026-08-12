package com.testknow.webtest.perf;

import java.util.List;

/**
 * 一个性能计划（performance 块）的完整结果：压测数据 + 阈值判定。
 */
public record PerfRunOutcome(
        PerfRunResult runResult,
        List<ThresholdVerifier.Violation> violations
) {
    public boolean isPass() {
        return violations.isEmpty();
    }
}
