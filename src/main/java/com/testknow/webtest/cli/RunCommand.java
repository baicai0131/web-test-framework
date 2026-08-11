package com.testknow.webtest.cli;

import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.ConfigLoader;
import com.testknow.webtest.config.ConfigValidator;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.core.TestRunner;
import com.testknow.webtest.core.result.ExecutionResult;
import com.testknow.webtest.report.ConsoleSummary;
import com.testknow.webtest.report.JsonResultWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * webtest run -c <config.yaml>
 */
@Command(
        name = "run",
        mixinStandardHelpOptions = true,
        description = "运行功能测试（读 YAML 配置 → 发请求 → 断言 → 出结果与报告）"
)
public class RunCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, required = true, paramLabel = "<yaml>",
            description = "配置文件路径")
    private Path config;

    @Option(names = {"-o", "--output-dir"}, paramLabel = "<dir>",
            description = "结果输出目录（默认 target/reports）")
    private Path outputDir = Path.of("target", "reports");

    @Option(names = {"-e", "--env"}, paramLabel = "<env>",
            description = "选择环境（覆盖配置中的 env 默认值）")
    private String env;

    @Override
    public Integer call() {
        try {
            ProjectConfig cfg = new ConfigLoader().load(config);
            new ConfigValidator(new AssertionRegistry()).validate(cfg);

            Path baseDir = config.toAbsolutePath().getParent();
            ExecutionResult result = new TestRunner(cfg, env, baseDir).run();
            new ConsoleSummary(System.out).print(result);
            new JsonResultWriter().write(result, outputDir);

            return result.isAllPass() ? ExitCodes.OK : ExitCodes.FUNCTIONAL_FAILURE;
        } catch (ConfigError e) {
            System.err.println("[配置错误] " + e.getMessage());
            return ExitCodes.CONFIG_ERROR;
        } catch (Exception e) {
            System.err.println("[内部错误] " + e.getMessage());
            e.printStackTrace(System.err);
            return ExitCodes.INTERNAL_ERROR;
        }
    }
}
