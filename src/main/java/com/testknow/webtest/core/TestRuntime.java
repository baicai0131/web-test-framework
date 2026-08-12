package com.testknow.webtest.core;

import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.model.EnvironmentConfig;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.config.model.SiteConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次执行的运行时上下文：解析环境（--env / config.env / 唯一环境）、
 * 生成生效的站点配置（baseUrl 覆盖）与根变量作用域（site + 环境变量合并）。
 * 功能测试（TestRunner）与性能测试（PerfRunner）共用同一套解析。
 */
public final class TestRuntime {

    private static final PlaceholderResolver RESOLVER = PlaceholderResolver.INSTANCE;

    private final String environmentName;
    private final SiteConfig effectiveSite;
    private final Variables rootVars;

    private TestRuntime(String environmentName, SiteConfig effectiveSite, Variables rootVars) {
        this.environmentName = environmentName;
        this.effectiveSite = effectiveSite;
        this.rootVars = rootVars;
    }

    public static TestRuntime resolve(ProjectConfig config, String envName) {
        Map<String, EnvironmentConfig> envs = config.getEnvironments();
        String resolved = resolveEnvironmentName(config, envName);
        EnvironmentConfig env = resolved == null ? null : (envs == null ? null : envs.get(resolved));
        SiteConfig effectiveSite = buildEffectiveSite(config, env);
        Variables rootVars = Variables.root(resolveConfigVariables(config, env));
        return new TestRuntime(resolved, effectiveSite, rootVars);
    }

    private static String resolveEnvironmentName(ProjectConfig config, String envName) {
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

    private static SiteConfig buildEffectiveSite(ProjectConfig config, EnvironmentConfig env) {
        SiteConfig site = config.getSite();
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

    private static Map<String, String> resolveConfigVariables(ProjectConfig config, EnvironmentConfig env) {
        Map<String, String> raw = new LinkedHashMap<>();
        if (config.getSite().getVariables() != null) {
            raw.putAll(config.getSite().getVariables());
        }
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

    public String environmentName() {
        return environmentName;
    }

    public SiteConfig effectiveSite() {
        return effectiveSite;
    }

    public Variables rootVars() {
        return rootVars;
    }
}
