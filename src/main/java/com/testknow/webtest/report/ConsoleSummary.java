package com.testknow.webtest.report;

import com.testknow.webtest.core.result.CaseResult;
import com.testknow.webtest.core.result.ExecutionResult;

import java.io.PrintStream;

/**
 * 控制台汇总输出（避免依赖彩色/特殊字符，保证各终端编码兼容）。
 */
public class ConsoleSummary {

    private final PrintStream out;

    public ConsoleSummary(PrintStream out) {
        this.out = out;
    }

    public void print(ExecutionResult result) {
        out.println();
        out.println("========== 测试结果汇总 ==========");
        out.printf("目标站点 : %s%n", result.getSiteName());
        if (result.getEnvironmentName() != null) {
            out.printf("环境     : %s%n", result.getEnvironmentName());
        }
        out.printf("用例总数 : %d    通过: %d    失败: %d%n",
                result.getTotal(), result.getPassed(), result.getFailed());
        out.printf("总耗时   : %d ms%n", result.getTotalElapsedMillis());
        out.println("---------------------------------");

        for (CaseResult c : result.getCases()) {
            String mark = c.isPass() ? "[PASS]" : (c.isError() ? "[ERROR]" : "[FAIL]");
            out.printf("%s %-6s %s  (status=%s, %dms)%n",
                    mark, c.getMethod(), c.getName(),
                    c.getStatusCode() == null ? "-" : c.getStatusCode(),
                    c.getElapsedMillis());
            if (c.getErrorMessage() != null) {
                out.println("      " + c.getErrorMessage());
            }
            c.getFailures().forEach(f -> out.println("      " + f.render()));
        }
        out.println("=================================");
    }
}
