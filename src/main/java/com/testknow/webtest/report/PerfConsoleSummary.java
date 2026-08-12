package com.testknow.webtest.report;

import com.testknow.webtest.perf.PerfRunOutcome;
import com.testknow.webtest.perf.ThresholdVerifier;
import com.testknow.webtest.perf.metrics.PerfAggregate;

import java.io.PrintStream;
import java.util.List;

/**
 * 性能测试控制台汇总。
 */
public class PerfConsoleSummary {

    private final PrintStream out;

    public PerfConsoleSummary(PrintStream out) {
        this.out = out;
    }

    public void print(List<PerfRunOutcome> outcomes) {
        out.println();
        out.println("========== 性能测试结果 ==========");
        boolean allPass = outcomes.stream().allMatch(PerfRunOutcome::isPass);
        out.printf("性能计划数 : %d    门禁: %s%n", outcomes.size(), allPass ? "全部通过" : "存在违规");
        out.println("---------------------------------");

        for (PerfRunOutcome outcome : outcomes) {
            var result = outcome.runResult();
            out.printf("%s [%s] 耗时 %dms%n",
                    outcome.isPass() ? "[PASS]" : "[FAIL]", result.planName(), result.elapsedMillis());
            for (PerfAggregate agg : result.aggregates().values()) {
                out.printf("  场景 %-20s 请求=%-6d 错误率=%.2f%% TPS=%.2f P50=%.2fms P95=%.2fms P99=%.2fms%n",
                        agg.scenarioName(), agg.totalRequests(), agg.errorRatePct(), agg.tpsMean(),
                        agg.p50Ms(), agg.p95Ms(), agg.p99Ms());
            }
            for (ThresholdVerifier.Violation v : outcome.violations()) {
                out.println("      [门禁] " + v.render());
            }
        }
        out.println("=================================");
    }
}
