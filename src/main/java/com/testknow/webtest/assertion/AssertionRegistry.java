package com.testknow.webtest.assertion;

import com.testknow.webtest.assertion.builtin.BodyContainsAssertion;
import com.testknow.webtest.assertion.builtin.HeaderAssertion;
import com.testknow.webtest.assertion.builtin.JsonPathAssertion;
import com.testknow.webtest.assertion.builtin.ResponseTimeAssertion;
import com.testknow.webtest.assertion.builtin.StatusAssertion;
import com.testknow.webtest.config.ConfigError;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 断言注册表：按 type 分发到具体断言工厂。
 * 未知断言类型在配置加载期即抛出清晰报错。
 * 后续阶段支持 ServiceLoader 注册自定义断言插件。
 */
public final class AssertionRegistry {

    @FunctionalInterface
    public interface Factory {
        Assertion create(Map<String, Object> spec);
    }

    private final Map<String, Factory> factories = new LinkedHashMap<>();

    public AssertionRegistry() {
        registerBuiltin();
    }

    private void registerBuiltin() {
        register("status", StatusAssertion::new);
        register("jsonpath", JsonPathAssertion::new);
        register("rtMs", ResponseTimeAssertion::new);
        register("bodyContains", BodyContainsAssertion::new);
        register("header", HeaderAssertion::new);
    }

    public void register(String type, Factory factory) {
        factories.put(type, factory);
    }

    /**
     * 解析一条原始 YAML 断言配置。配置结构错误时抛出 {@link ConfigError}。
     */
    public Assertion parse(Map<String, Object> spec) {
        if (spec == null || spec.isEmpty()) {
            throw new ConfigError("断言配置不能为空");
        }
        Object typeObj = spec.get("type");
        if (typeObj == null || String.valueOf(typeObj).isBlank()) {
            throw new ConfigError("断言缺少 type 字段: " + spec);
        }
        String type = String.valueOf(typeObj);
        Factory factory = factories.get(type);
        if (factory == null) {
            throw new ConfigError("未知断言类型: '" + type + "'，可选: " + factories.keySet());
        }
        try {
            return factory.create(spec);
        } catch (ConfigError e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConfigError("断言 '" + type + "' 配置值不合法: " + e.getMessage(), e);
        }
    }

    public java.util.Set<String> types() {
        return factories.keySet();
    }
}
