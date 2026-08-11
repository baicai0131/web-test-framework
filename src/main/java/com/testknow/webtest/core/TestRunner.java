package com.testknow.webtest.core;

import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.model.DataSetConfig;
import com.testknow.webtest.config.model.EnvironmentConfig;
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
import java.util.LinkedHashMap;
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

    private static final PlaceholderResolver RESOLVER = PlaceholderResolver.INSTANCE;

    private final ProjectConfig config;
    private final String envName;         // --env 指定，可空
    private final Path baseDir;           // 配置文件所在目录（解析 CSV 相对路径）
    private final String resolvedEnvName; // 实际生效的环境名
    private final SiteConfig effectiveSite;
    private final Variables rootVars;
    private final Map<String, TestCaseConfig> testsByName;

    public TestRunner(ProjectConfig config, String envName, Path baseDir) {
        this.config = config;
        this.envName = envName;
        this.baseDir = baseDir;
        this.resolvedEnvName = resolveEnvironmentName();
        this.effectiveSite = buildEffectiveSite();
        this.rootVars = Variables.root(resolveConfigVariables());
        this.testsByName = config.getTests().stream()
                .collect(Collectors.toMap(TestCaseConfig::getName, Function.identity(),
                        (a, b) -> {
                            throw new ConfigError("存在重名的测试用例: " + a.getName());
                        }));
    }

    public ExecutionResult run() {
        HttpCaller caller = new HttpCaller(effectiveSite);
        AssertionRegistry assertions = new AssertionRegistry();
        ExtractorRegistry extractors = new ExtractorRegistry();
        RequestBuilder requestBuilder = new RequestBuilder(effectiveSite);
        CaseExecutor caseExecutor = new CaseExecutor(caller, requestBuilder, assertions, extractors);

        long start = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        List<CaseResult> results = new ArrayList<>();

        log.info("开始执行，站点: {} 环境: {} 用例数: {}",
                effectiveSite.getName(), resolvedEnvName == null ? "(默认)" : resolvedEnvName,
                config.getTests().size());

        AuthManager auth = new AuthManager(config.getAuth(), caller, requestBuilder, assertions, extractors, rootVars);
        if (auth.isEnabled()) {
            CaseResult setup = auth.login();
            if (setup != null) {
                results.add(setup);
            }
        }

        Variables vars = rootVars;
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
        ExecutionResult result = new ExecutionResult(effectiveSite.getName(), resolvedEnvName,
                start, elapsed, results);
        log.info("执行完成: 共 {}，通过 {}，失败 {}", result.getTotal(), result.getPassed(), result.getFailed());
        return result;
    }

    // ---------- 环境解析 ----------

    private String resolveEnvironmentName() {
        Map<String, EnvironmentConfig> envs = config.getEnvironments();
        String name = envName != null ? envName : config.getEnv();
        if (name != null) {
            if (envs == null || !envs.containsKey(name)) {
                throw new ConfigError("未定义环境: '" + name + "'，可选: "
                        + (envs == null || envs.isEmpty() ? "无" : envs.keySet()));
            }
            return name;
        }
        if (envs != null && envs.size() == 1) {
            return envs.keySet().iterator().next();
        }
        return null;
    }

    private EnvironmentConfig resolveEnvironment() {
        if (resolvedEnvName == null) {
            return null;
        }
        return config.getEnvironments().get(resolvedEnvName);
    }

    private SiteConfig buildEffectiveSite() {
        SiteConfig site = config.getSite();
        EnvironmentConfig env = resolveEnvironment();
        if (env == null || env.getBaseUrl() == null || env.getBaseUrl().isBlank()) {
            return site;
        }
        SiteConfig copy = new SiteConfig();
        copy.setName(site.getName());
        copy.setBaseUrl(env.getBaseUrl());
        copy.setGlobalHeaders(site.getGlobalHeaders());
        copy.setVariables(site.getVariables());
        copy.setTimeouts(site.getTimeouts());
        copy.setRetry(site.getRetry());
        return copy;
    }

    private Map<String, String> resolveConfigVariables() {
        Map<String, String> raw = new LinkedHashMap<>();
        if (config.getSite().getVariables() != null) {
            raw.putAll(config.getSite().getVariables());
        }
        EnvironmentConfig env = resolveEnvironment();
        if (env != null && env.getVariables() != null) {
            raw.putAll(env.getVariables());
        }
        return resolveVariables(raw);
    }

    /** 迭代解析配置变量中的 ${env.X} / ${variables.X} / 内置函数，带循环检测。 */
    static Map<String, String> resolveVariables(Map<String, String> raw) {
        Map<String, String> current = new LinkedHashMap<>(raw);
        for (int pass = 0; pass < 6; pass++) {
            Variables scope = Variables.root(current);
            boolean anyChange = false;
            Map<String, String> next = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : current.entrySet()) {
                String v = e.getValue();
                if (v != null && v.contains("${")) {
                    v = RESOLVER.resolve(v, scope);
                    anyChange = true;
                }
                next.put(e.getKey(), v);
            }
            current = next;
            if (!anyChange) {
                break;
            }
        }
        for (Map.Entry<String, String> e : current.entrySet()) {
            if (e.getValue() != null && e.getValue().contains("${")) {
                throw new ConfigError("配置变量无法解析（疑似循环引用或引用未定义变量）: "
                        + e.getKey() + " = " + e.getValue());
            }
        }
        return current;
    }
}
