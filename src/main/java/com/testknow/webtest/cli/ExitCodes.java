package com.testknow.webtest.cli;

/**
 * 退出码契约（CI 门禁依据）。
 * <pre>
 * 0 = 全部通过
 * 1 = 功能断言失败
 * 2 = 性能阈值违规（预留，后续性能引擎）
 * 3 = 配置 / 参数错误
 * 4 = 安全检查失败（预留，后续安全模块）
 * 5 = 内部错误
 * </pre>
 */
public final class ExitCodes {
    public static final int OK = 0;
    public static final int FUNCTIONAL_FAILURE = 1;
    public static final int PERF_THRESHOLD = 2;
    public static final int CONFIG_ERROR = 3;
    public static final int SECURITY_FAILURE = 4;
    public static final int INTERNAL_ERROR = 5;

    private ExitCodes() {
    }
}
