package com.testknow.webtest.core.result;

import com.testknow.webtest.assertion.AssertionFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单用例执行结果。附带本次提取出的变量（供关联传递与报告排查）。
 */
public class CaseResult {

    private final String name;
    private final String method;
    private final String path;
    private final Integer statusCode;       // 网络失败时为 null
    private final long elapsedNanos;
    private final List<AssertionFailure> failures;
    private final String errorMessage;      // 网络/内部错误信息，正常时为 null
    private final Map<String, String> extractedVars;

    public CaseResult(String name, String method, String path, Integer statusCode,
                      long elapsedNanos, List<AssertionFailure> failures, String errorMessage) {
        this(name, method, path, statusCode, elapsedNanos, failures, errorMessage, Map.of());
    }

    public CaseResult(String name, String method, String path, Integer statusCode,
                      long elapsedNanos, List<AssertionFailure> failures, String errorMessage,
                      Map<String, String> extractedVars) {
        this.name = name;
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.elapsedNanos = elapsedNanos;
        this.failures = failures == null ? new ArrayList<>() : failures;
        this.errorMessage = errorMessage;
        this.extractedVars = extractedVars == null ? Map.of() : extractedVars;
    }

    public String getName() {
        return name;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public long getElapsedNanos() {
        return elapsedNanos;
    }

    public long getElapsedMillis() {
        return elapsedNanos / 1_000_000;
    }

    public List<AssertionFailure> getFailures() {
        return failures;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, String> getExtractedVars() {
        return extractedVars;
    }

    public boolean isPass() {
        return errorMessage == null && failures.isEmpty();
    }

    public boolean isError() {
        return errorMessage != null;
    }

    /** 复制一份仅改名（数据驱动每行加后缀用）。 */
    public CaseResult withName(String newName) {
        return new CaseResult(newName, method, path, statusCode, elapsedNanos, failures, errorMessage, extractedVars);
    }
}
