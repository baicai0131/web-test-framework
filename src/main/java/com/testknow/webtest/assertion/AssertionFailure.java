package com.testknow.webtest.assertion;

/**
 * 断言失败的结构化差异信息，供报告可读展示。
 */
public record AssertionFailure(
        String check,     // 断言类型，如 status / jsonpath / rtMs
        String expected,
        String actual,
        String path       // 定位信息，如 JSONPath 表达式或响应头名
) {

    /** 单行渲染，如: [status] expected 201 but was 200 */
    public String render() {
        String loc = (path == null || path.isBlank()) ? "" : " @ " + path;
        return String.format("[%s] expected %s but was %s%s",
                check, expected, actual, loc);
    }
}
