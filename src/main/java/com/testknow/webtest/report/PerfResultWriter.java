package com.testknow.webtest.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testknow.webtest.perf.PerfRunOutcome;
import com.testknow.webtest.perf.PerfRunResult;
import com.testknow.webtest.perf.ThresholdVerifier;
import com.testknow.webtest.perf.metrics.PerfAggregate;
import com.testknow.webtest.perf.metrics.TimeSeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 性能结果写入 JSON：每个计划的聚合指标 + 阈值判定 + 时间序列。
 */
public class PerfResultWriter {

    public static final int SCHEMA_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger(PerfResultWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Path write(List<PerfRunOutcome> outcomes, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            Path file = outputDir.resolve("perf-result.json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), toMap(outcomes));
            log.info("性能结果已写入: {}", file.toAbsolutePath());
            return file;
        } catch (IOException e) {
            throw new RuntimeException("写入性能结果失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> toMap(List<PerfRunOutcome> outcomes) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("tool", "web-test-framework-perf");
        boolean allPass = outcomes.stream().allMatch(PerfRunOutcome::isPass);
        root.put("pass", allPass);

        List<Map<String, Object>> plans = new ArrayList<>();
        for (PerfRunOutcome outcome : outcomes) {
            plans.add(toPlanMap(outcome));
        }
        root.put("plans", plans);
        return root;
    }

    private Map<String, Object> toPlanMap(PerfRunOutcome outcome) {
        PerfRunResult result = outcome.runResult();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("name", result.planName());
        plan.put("elapsedMillis", result.elapsedMillis());
        plan.put("pass", outcome.isPass());

        List<Map<String, Object>> violations = new ArrayList<>();
        for (ThresholdVerifier.Violation v : outcome.violations()) {
            violations.add(Map.of(
                    "scenario", v.scenario(),
                    "metric", v.metric(),
                    "expected", v.expected(),
                    "actual", v.actual()));
        }
        plan.put("violations", violations);

        List<Map<String, Object>> scenarios = new ArrayList<>();
        for (PerfAggregate agg : result.aggregates().values()) {
            scenarios.add(aggregateToMap(agg));
        }
        plan.put("scenarios", scenarios);

        // 时间序列（所有场景合并，用于报告曲线）
        List<Map<String, Object>> series = new ArrayList<>();
        for (TimeSeriesPoint p : result.series()) {
            series.add(Map.of(
                    "t", p.epochSecond(),
                    "tps", p.tps(),
                    "avgRtMs", p.avgRtMs(),
                    "p95RtMs", p.p95RtMs(),
                    "errorRatePct", p.errorRatePct()));
        }
        plan.put("series", series);
        return plan;
    }

    private static Map<String, Object> aggregateToMap(PerfAggregate agg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scenario", agg.scenarioName());
        m.put("totalRequests", agg.totalRequests());
        m.put("errorCount", agg.errorCount());
        m.put("errorRatePct", round(agg.errorRatePct()));
        m.put("tpsMean", round(agg.tpsMean()));
        m.put("avgRtMs", round(agg.avgRtMs()));
        m.put("p50Ms", round(agg.p50Ms()));
        m.put("p90Ms", round(agg.p90Ms()));
        m.put("p95Ms", round(agg.p95Ms()));
        m.put("p99Ms", round(agg.p99Ms()));
        m.put("p999Ms", round(agg.p999Ms()));
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
