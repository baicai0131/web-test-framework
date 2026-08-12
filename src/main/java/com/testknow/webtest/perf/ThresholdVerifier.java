package com.testknow.webtest.perf;

import com.testknow.webtest.config.model.PerfConfig;
import com.testknow.webtest.config.model.PerfThresholdsConfig;
import com.testknow.webtest.perf.metrics.PerfAggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 性能阈值门禁：对每个场景的聚合指标与配置阈值比对。
 * 任一违规 → 该性能计划判定失败（CI 退出码 2）。
 */
public final class ThresholdVerifier {

    public record Violation(String scenario, String metric, String expected, String actual) {
        public String render() {
            return String.format("[%s] %s: 期望 %s，实际 %s", scenario, metric, expected, actual);
        }
    }

    private ThresholdVerifier() {
    }

    /** 检查一个性能计划的所有场景。返回违规列表（空 = 全部通过）。 */
    public static List<Violation> check(PerfConfig plan, Map<String, PerfAggregate> aggregates) {
        PerfThresholdsConfig t = plan.getThresholds();
        if (t == null || t.isEmpty()) {
            return List.of();
        }
        List<Violation> violations = new ArrayList<>();
        for (PerfAggregate agg : aggregates.values()) {
            if (t.getErrorRateMaxPct() != null && agg.errorRatePct() > t.getErrorRateMaxPct()) {
                violations.add(new Violation(agg.scenarioName(), "错误率",
                        "≤ " + t.getErrorRateMaxPct() + "%", agg.errorRatePct() + "%"));
            }
            if (t.getP95MsMax() != null && agg.p95Ms() > t.getP95MsMax()) {
                violations.add(new Violation(agg.scenarioName(), "P95",
                        "≤ " + t.getP95MsMax() + "ms", agg.p95Ms() + "ms"));
            }
            if (t.getP99MsMax() != null && agg.p99Ms() > t.getP99MsMax()) {
                violations.add(new Violation(agg.scenarioName(), "P99",
                        "≤ " + t.getP99MsMax() + "ms", agg.p99Ms() + "ms"));
            }
            if (t.getTpsMin() != null && agg.tpsMean() < t.getTpsMin()) {
                violations.add(new Violation(agg.scenarioName(), "TPS",
                        "≥ " + t.getTpsMin(), String.valueOf(agg.tpsMean())));
            }
            if (t.getTotalRequestsMin() != null && agg.totalRequests() < t.getTotalRequestsMin()) {
                violations.add(new Violation(agg.scenarioName(), "总请求数",
                        "≥ " + t.getTotalRequestsMin(), String.valueOf(agg.totalRequests())));
            }
        }
        return violations;
    }
}
