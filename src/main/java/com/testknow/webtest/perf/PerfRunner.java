package com.testknow.webtest.perf;

import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.model.PerfConfig;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.CaseExecutor;
import com.testknow.webtest.core.TestRuntime;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.auth.AuthManager;
import com.testknow.webtest.core.extract.ExtractorRegistry;
import com.testknow.webtest.http.HttpCaller;
import com.testknow.webtest.http.RequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 性能测试运行器：对配置中的一个或多个 performance 计划执行压测，
 * 每个计划产出聚合指标 + 时间序列 + 阈值判定。
 */
public class PerfRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfRunner.class);

    private final ProjectConfig config;
    private final String envName;
    private final String perfName;    // 可选：只跑某个 performance 块
    private final TestRuntime runtime;
    private final Map<String, TestCaseConfig> testsByName;

    public PerfRunner(ProjectConfig config, String envName, String perfName) {
        this.config = config;
        this.envName = envName;
        this.perfName = perfName;
        this.runtime = TestRuntime.resolve(config, envName);
        this.testsByName = config.getTests().stream()
                .collect(Collectors.toMap(TestCaseConfig::getName, Function.identity(),
                        (a, b) -> {
                            throw new ConfigError("存在重名的测试用例: " + a.getName());
                        }));
    }

    public List<PerfRunOutcome> run() {
        List<PerfConfig> plans = selectPlans();
        if (plans.isEmpty()) {
            throw new ConfigError("配置中没有 performance 块"
                    + (perfName == null ? "" : "，或找不到名称为 '" + perfName + "' 的性能计划"));
        }

        var site = runtime.effectiveSite();
        HttpCaller caller = new HttpCaller(site);
        AssertionRegistry assertions = new AssertionRegistry();
        ExtractorRegistry extractors = new ExtractorRegistry();
        CaseExecutor caseExecutor = new CaseExecutor(caller, new RequestBuilder(site), assertions, extractors);
        AuthManager auth = new AuthManager(config.getAuth(), caller, new RequestBuilder(site),
                assertions, extractors, runtime.rootVars());

        List<PerfRunOutcome> outcomes = new ArrayList<>();
        for (PerfConfig plan : plans) {
            log.info("======== 压测计划 [{}] 开始 ========", plan.getName());
            LoadGenerator generator = new LoadGenerator(plan, testsByName::get, caseExecutor, auth, runtime.rootVars());
            PerfRunResult result = generator.run();
            List<ThresholdVerifier.Violation> violations = ThresholdVerifier.check(plan, result.aggregates());
            outcomes.add(new PerfRunOutcome(result, violations));
            log.info("======== 压测计划 [{}] 结束: {} ========",
                    plan.getName(), violations.isEmpty() ? "门禁通过" : "门禁违规 " + violations.size() + " 项");
        }
        return outcomes;
    }

    private List<PerfConfig> selectPlans() {
        List<PerfConfig> all = config.getPerformance();
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        if (perfName == null) {
            return all;
        }
        return all.stream().filter(p -> perfName.equals(p.getName())).collect(Collectors.toList());
    }

    public TestRuntime runtime() {
        return runtime;
    }
}
