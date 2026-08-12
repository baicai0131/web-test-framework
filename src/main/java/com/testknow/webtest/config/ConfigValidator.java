package com.testknow.webtest.config;

import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.model.AuthConfig;
import com.testknow.webtest.config.model.DataSetConfig;
import com.testknow.webtest.config.model.PerfConfig;
import com.testknow.webtest.config.model.PerfScenarioConfig;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.extract.ExtractorRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置语义校验：必填字段、URL 格式、HTTP 方法、断言类型、提取类型、鉴权、数据驱动。
 * 全部在加载期失败，给出清晰的错误定位（用例名 + 字段）。
 */
public class ConfigValidator {

    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private static final Set<String> AUTH_TYPES = Set.of("none", "bearertoken", "basic", "login");
    private static final Set<String> AUTH_TYPE_NAMES = Set.of("none", "bearerToken", "basic", "login");

    private final AssertionRegistry assertions;
    private final ExtractorRegistry extractors;

    public ConfigValidator(AssertionRegistry assertions) {
        this(assertions, new ExtractorRegistry());
    }

    public ConfigValidator(AssertionRegistry assertions, ExtractorRegistry extractors) {
        this.assertions = assertions;
        this.extractors = extractors;
    }

    public void validate(ProjectConfig config) {
        if (config == null) {
            throw new ConfigError("配置为空");
        }
        validateSite(config);
        validateTests(config);
        validateAuth(config);
        validateDataSets(config);
        validatePerformance(config);
    }

    private void validateSite(ProjectConfig config) {
        var site = config.getSite();
        if (site == null) {
            throw new ConfigError("缺少 site 配置");
        }
        if (site.getBaseUrl() == null || site.getBaseUrl().isBlank()) {
            throw new ConfigError("site.baseUrl 未配置");
        }
        if (!site.getBaseUrl().matches("^https?://.*")) {
            throw new ConfigError("site.baseUrl 必须以 http:// 或 https:// 开头: " + site.getBaseUrl());
        }
    }

    private void validateTests(ProjectConfig config) {
        List<TestCaseConfig> tests = config.getTests();
        if (tests == null || tests.isEmpty()) {
            throw new ConfigError("tests 列表为空，至少需要一个测试用例");
        }
        Set<String> names = new HashSet<>();
        for (int i = 0; i < tests.size(); i++) {
            validateTest(tests.get(i), i);
            if (!names.add(tests.get(i).getName())) {
                throw new ConfigError("存在重名的测试用例: " + tests.get(i).getName());
            }
        }
    }

    private void validateTest(TestCaseConfig tc, int index) {
        String label = "tests[" + index + "]";
        if (tc.getName() == null || tc.getName().isBlank()) {
            throw new ConfigError(label + " 缺少 name");
        }
        if (tc.getPath() == null || tc.getPath().isBlank()) {
            throw new ConfigError("用例 '" + tc.getName() + "' 缺少 path");
        }
        if (!tc.getPath().startsWith("/")) {
            throw new ConfigError("用例 '" + tc.getName() + "' 的 path 必须以 / 开头: " + tc.getPath());
        }
        if (!HTTP_METHODS.contains(tc.getMethod().toUpperCase())) {
            throw new ConfigError("用例 '" + tc.getName() + "' 的 method 非法: " + tc.getMethod()
                    + "，可选: " + HTTP_METHODS);
        }
        validateAsserts(tc);
        validateExtract(tc);
    }

    private void validateAsserts(TestCaseConfig tc) {
        if (tc.getAsserts() == null || tc.getAsserts().isEmpty()) {
            return;
        }
        for (Map<String, Object> spec : tc.getAsserts()) {
            try {
                assertions.parse(spec);
            } catch (ConfigError e) {
                throw new ConfigError("用例 '" + tc.getName() + "' 断言配置错误: " + e.getMessage(), e);
            }
        }
    }

    private void validateExtract(TestCaseConfig tc) {
        if (tc.getExtract() == null || tc.getExtract().isEmpty()) {
            return;
        }
        for (Map<String, Object> spec : tc.getExtract()) {
            if (spec.get("name") == null || String.valueOf(spec.get("name")).isBlank()) {
                throw new ConfigError("用例 '" + tc.getName() + "' 的 extract 缺少 name 字段: " + spec);
            }
            try {
                extractors.parse(spec);
            } catch (ConfigError e) {
                throw new ConfigError("用例 '" + tc.getName() + "' 提取配置错误: " + e.getMessage(), e);
            }
        }
    }

    private void validateAuth(ProjectConfig config) {
        AuthConfig auth = config.getAuth();
        if (auth == null || auth.getType() == null || "none".equalsIgnoreCase(auth.getType())) {
            return;
        }
        String type = auth.getType().toLowerCase();
        if (!AUTH_TYPES.contains(type)) {
            throw new ConfigError("未知 auth.type: '" + auth.getType() + "'，可选: " + AUTH_TYPE_NAMES);
        }
        switch (type) {
            case "bearertoken" -> {
                if (auth.getToken() == null || auth.getToken().isBlank()) {
                    throw new ConfigError("auth.type=bearerToken 需要配置 auth.token");
                }
            }
            case "basic" -> {
                if (auth.getUsername() == null || auth.getPassword() == null) {
                    throw new ConfigError("auth.type=basic 需要配置 auth.username 和 auth.password");
                }
            }
            case "login" -> {
                TestCaseConfig login = auth.getLogin();
                if (login == null) {
                    throw new ConfigError("auth.type=login 需要配置 auth.login");
                }
                if (login.getExtract() == null || login.getExtract().isEmpty()) {
                    throw new ConfigError("auth.type=login 的 auth.login 需要 extract（至少提取一个变量）");
                }
                if (auth.getInjectHeader() == null || auth.getInjectHeader().isEmpty()) {
                    throw new ConfigError("auth.type=login 需要配置 auth.injectHeader（如 Authorization: Bearer ${token}）");
                }
            }
            default -> {
            }
        }
    }

    private void validatePerformance(ProjectConfig config) {
        List<PerfConfig> list = config.getPerformance();
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<String> testNames = new HashSet<>();
        for (TestCaseConfig t : config.getTests()) {
            testNames.add(t.getName());
        }
        for (int i = 0; i < list.size(); i++) {
            PerfConfig perf = list.get(i);
            String label = "performance[" + i + "]";
            if (perf.getName() == null || perf.getName().isBlank()) {
                throw new ConfigError(label + " 缺少 name");
            }
            boolean hasDuration = perf.usesDuration();
            boolean hasIterations = perf.getIterations() != null && perf.getIterations() > 0;
            if (!hasDuration && !hasIterations) {
                throw new ConfigError(label + " ('" + perf.getName() + "') 需要 durationSec 或 iterations 之一");
            }
            if (hasDuration && hasIterations) {
                throw new ConfigError(label + " ('" + perf.getName() + "') 的 durationSec 与 iterations 互斥，只能选其一");
            }
            if (perf.getScenarios() == null || perf.getScenarios().isEmpty()) {
                throw new ConfigError(label + " ('" + perf.getName() + "') 缺少 scenarios");
            }
            for (int j = 0; j < perf.getScenarios().size(); j++) {
                PerfScenarioConfig sc = perf.getScenarios().get(j);
                String sLabel = label + ".scenarios[" + j + "]";
                if (sc.getRef() == null || sc.getRef().isBlank()) {
                    throw new ConfigError(sLabel + " 缺少 ref（引用用例名）");
                }
                if (!testNames.contains(sc.getRef())) {
                    throw new ConfigError(sLabel + " 引用的用例不存在: " + sc.getRef());
                }
                if (sc.getUsers() <= 0) {
                    throw new ConfigError(sLabel + " 的 users 必须 > 0");
                }
                if (sc.getRampUpSec() < 0) {
                    throw new ConfigError(sLabel + " 的 rampUpSec 不能为负");
                }
            }
        }
    }

    private void validateDataSets(ProjectConfig config) {
        List<DataSetConfig> dataSets = config.getDataSets();
        if (dataSets == null || dataSets.isEmpty()) {
            return;
        }
        Set<String> testNames = new HashSet<>();
        for (TestCaseConfig t : config.getTests()) {
            testNames.add(t.getName());
        }
        for (int i = 0; i < dataSets.size(); i++) {
            DataSetConfig ds = dataSets.get(i);
            String label = "dataSets[" + i + "]";
            if (ds.getTest() == null || ds.getTest().isBlank()) {
                throw new ConfigError(label + " 缺少 test（引用用例名）");
            }
            if (!testNames.contains(ds.getTest())) {
                throw new ConfigError(label + " 引用的用例不存在: " + ds.getTest());
            }
            if (ds.getFile() == null || ds.getFile().isBlank()) {
                throw new ConfigError(label + " 缺少 file");
            }
            if (ds.getMode() != null && !"each".equalsIgnoreCase(ds.getMode())) {
                throw new ConfigError(label + " 的 mode 不支持: '" + ds.getMode() + "'，当前仅支持 each");
            }
            for (DataSetConfig.BindColumn b : ds.getBind()) {
                if (b.getName() == null || b.getName().isBlank() || b.getColumn() == null || b.getColumn().isBlank()) {
                    throw new ConfigError(label + " 的 bind 需要 name 与 column 字段");
                }
            }
        }
    }
}
