package com.testknow.webtest.cli;

import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.ConfigLoader;
import com.testknow.webtest.config.ConfigValidator;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.perf.PerfRunOutcome;
import com.testknow.webtest.perf.PerfRunner;
import com.testknow.webtest.report.PerfConsoleSummary;
import com.testknow.webtest.report.PerfResultWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * webtest perf -c <config.yaml> [-p <perfName>]
 */
@Command(
        name = "perf",
        mixinStandardHelpOptions = true,
        description = "运行性能测试（并发压测 → TPS/响应时间分位 → 阈值门禁）"
)
public class PerfCommand implements Callable<Integer> {

    @Option(names = {"-c", "--config"}, required = true, paramLabel = "<yaml>",
            description = "配置文件路径")
    private Path config;

    @Option(names = {"-o", "--output-dir"}, paramLabel = "<dir>",
            description = "结果输出目录（默认 target/reports）")
    private Path outputDir = Path.of("target", "reports");

    @Option(names = {"-e", "--env"}, paramLabel = "<env>",
            description = "选择环境（覆盖配置中的 env 默认值）")
    private String env;

    @Option(names = {"-p", "--perf"}, paramLabel = "<perfName>",
            description = "只运行指定名称的性能计划（默认运行全部）")
    private String perfName;

    @Override
    public Integer call() {
        try {
            ProjectConfig cfg = new ConfigLoader().load(config);
            new ConfigValidator(new AssertionRegistry()).validate(cfg);

            List<PerfRunOutcome> outcomes = new PerfRunner(cfg, env, perfName).run();
            new PerfConsoleSummary(System.out).print(outcomes);
            new PerfResultWriter().write(outcomes, outputDir);

            boolean allPass = outcomes.stream().allMatch(PerfRunOutcome::isPass);
            return allPass ? ExitCodes.OK : ExitCodes.PERF_THRESHOLD;
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
