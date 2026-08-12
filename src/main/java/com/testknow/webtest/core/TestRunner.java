package com.testknow.webtest.core;

import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.model.DataSetConfig;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.config.model.SiteConfig;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.auth.AuthManager;
import com.testknow.webtest.core.dataset.DataSetRunner;
import com.testknow.webtest.core.extract.ExtractorRegistry;
import com.testknow.webtest.core.result.CaseResult;
import com.testknow.webtest.core.result.ExecutionResult;
import com.testknow.webtest.http.HttpCaller;
import com.testknow.webtest.http.RequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 测试运行器：
 * <ol>
 *   <li>解析环境（--env / config.env / 唯一环境），决定 baseUrl 与配置变量</li>
 *   <li>初始化鉴权（登录提取 token → 注入请求头）</li>
 *   <li>顺序执行功能用例，提取变量沿作用域链向后传递（登录拿 token 关联）</li>
 *   <li>执行数据驱动（CSV 每行跑引用用例）</li>
 *   <li>汇总为 {@link ExecutionResult}</li>
 * </ol>
 */
public class TestRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    private final ProjectConfig config;
    private final Path baseDir;           // 配置文件所在目录（解析 CSV 相对路径）
    private final TestRuntime runtime;    // 环境解析 + 生效站点 + 根变量
    private final Map<String, TestCaseConfig> testsByName;

    public TestRunner(ProjectConfig config, String envName, Path baseDir) {
        this.config = config;
        this.baseDir = baseDir;
        this.runtime = TestRuntime.resolve(config, envName);
        this.testsByName = config.getTests().stream()
                .collect(Collectors.toMap(TestCaseConfig::getName, Function.identity(),
                        (a, b) -> {
                            throw new ConfigError("存在重名的测试用例: " + a.getName());
                        }));
    }

    public ExecutionResult run() {
        SiteConfig effectiveSite = runtime.effectiveSite();
        HttpCaller caller = new HttpCaller(effectiveSite);
        AssertionRegistry assertions = new AssertionRegistry();
        ExtractorRegistry extractors = new ExtractorRegistry();
        RequestBuilder requestBuilder = new RequestBuilder(effectiveSite);
        CaseExecutor caseExecutor = new CaseExecutor(caller, requestBuilder, assertions, extractors);

        long start = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        List<CaseResult> results = new ArrayList<>();

        log.info("开始执行，站点: {} 环境: {} 用例数: {}",
                effectiveSite.getName(), runtime.environmentName() == null ? "(默认)" : runtime.environmentName(),
                config.getTests().size());

        AuthManager auth = new AuthManager(config.getAuth(), caller, requestBuilder, assertions, extractors, runtime.rootVars());
        if (auth.isEnabled()) {
            CaseResult setup = auth.login();
            if (setup != null) {
                results.add(setup);
            }
        }

        Variables vars = runtime.rootVars();
        for (TestCaseConfig tc : config.getTests()) {
            log.info("执行用例 [{}] {} {}", tc.getName(), tc.getMethod(), tc.getPath());
            CaseResult r = caseExecutor.execute(tc, vars, auth);
            results.add(r);
            if (!r.getExtractedVars().isEmpty()) {
                vars = vars.child(r.getExtractedVars());
            }
        }

        if (config.getDataSets() != null && !config.getDataSets().isEmpty()) {
            DataSetRunner dsRunner = new DataSetRunner(caseExecutor, baseDir);
            for (DataSetConfig ds : config.getDataSets()) {
                TestCaseConfig tc = testsByName.get(ds.getTest());
                results.addAll(dsRunner.run(ds, tc, vars, auth));
            }
        }

        long elapsed = System.nanoTime() - startNanos;
        ExecutionResult result = new ExecutionResult(effectiveSite.getName(), runtime.environmentName(),
                start, elapsed, results);
        log.info("执行完成: 共 {}，通过 {}，失败 {}", result.getTotal(), result.getPassed(), result.getFailed());
        return result;
    }
}
