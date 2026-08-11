package com.testknow.webtest.core.result;

import java.util.List;

/**
 * 整次执行汇总结果。
 */
public class ExecutionResult {

    private final String siteName;
    private final String environmentName;
    private final long startEpochMillis;
    private final long totalElapsedNanos;
    private final List<CaseResult> cases;

    public ExecutionResult(String siteName, String environmentName, long startEpochMillis,
                           long totalElapsedNanos, List<CaseResult> cases) {
        this.siteName = siteName;
        this.environmentName = environmentName;
        this.startEpochMillis = startEpochMillis;
        this.totalElapsedNanos = totalElapsedNanos;
        this.cases = cases;
    }

    public String getSiteName() {
        return siteName;
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    public long getStartEpochMillis() {
        return startEpochMillis;
    }

    public long getTotalElapsedNanos() {
        return totalElapsedNanos;
    }

    public long getTotalElapsedMillis() {
        return totalElapsedNanos / 1_000_000;
    }

    public List<CaseResult> getCases() {
        return cases;
    }

    public int getTotal() {
        return cases.size();
    }

    public int getPassed() {
        return (int) cases.stream().filter(CaseResult::isPass).count();
    }

    public int getFailed() {
        return (int) cases.stream().filter(c -> !c.isPass()).count();
    }

    public boolean isAllPass() {
        return cases.stream().allMatch(CaseResult::isPass);
    }
}
