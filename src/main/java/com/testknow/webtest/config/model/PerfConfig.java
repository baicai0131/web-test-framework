package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个性能测试计划。
 *
 * <pre>
 * - name: mixed-browsing
 *   durationSec: 60         # 停止条件：时长（秒）或 iterations 二选一
 *   scenarios:
 *     - { ref: get-json, users: 30, rampUpSec: 20, thinkTimeMs: 200 }
 *   thresholds:
 *     errorRateMaxPct: 1.0
 *     p95MsMax: 500
 *     tpsMin: 100
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerfConfig {

    private String name;
    private Integer durationSec;       // 停止条件一：总时长（秒）
    private Integer iterations;        // 停止条件二：每个用户迭代次数（与 durationSec 互斥）
    private List<PerfScenarioConfig> scenarios = new ArrayList<>();
    private PerfThresholdsConfig thresholds = new PerfThresholdsConfig();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public Integer getIterations() {
        return iterations;
    }

    public void setIterations(Integer iterations) {
        this.iterations = iterations;
    }

    public List<PerfScenarioConfig> getScenarios() {
        return scenarios;
    }

    public void setScenarios(List<PerfScenarioConfig> scenarios) {
        this.scenarios = scenarios;
    }

    public PerfThresholdsConfig getThresholds() {
        return thresholds;
    }

    public void setThresholds(PerfThresholdsConfig thresholds) {
        this.thresholds = thresholds;
    }

    public boolean usesDuration() {
        return durationSec != null && durationSec > 0;
    }
}
