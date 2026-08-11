package com.testknow.webtest.core.extract;

import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.core.extract.builtin.BodyExtractor;
import com.testknow.webtest.core.extract.builtin.HeaderExtractor;
import com.testknow.webtest.core.extract.builtin.JsonPathExtractor;
import com.testknow.webtest.core.extract.builtin.RegexExtractor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 提取器注册表：按 type 分发到具体提取器工厂。未知类型在加载期报错。
 */
public final class ExtractorRegistry {

    @FunctionalInterface
    public interface Factory {
        Extractor create(Map<String, Object> spec);
    }

    private final Map<String, Factory> factories = new LinkedHashMap<>();

    public ExtractorRegistry() {
        register("jsonpath", JsonPathExtractor::new);
        register("regex", RegexExtractor::new);
        register("header", HeaderExtractor::new);
        register("body", BodyExtractor::new);
    }

    public void register(String type, Factory factory) {
        factories.put(type, factory);
    }

    public Extractor parse(Map<String, Object> spec) {
        Object typeObj = spec.get("type");
        if (typeObj == null || String.valueOf(typeObj).isBlank()) {
            throw new ConfigError("extract 缺少 type 字段: " + spec);
        }
        String type = String.valueOf(typeObj);
        Factory factory = factories.get(type);
        if (factory == null) {
            throw new ConfigError("未知 extract 类型: '" + type + "'，可选: " + factories.keySet());
        }
        try {
            return factory.create(spec);
        } catch (ConfigError e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConfigError("extract '" + type + "' 配置值不合法: " + e.getMessage(), e);
        }
    }

    public Set<String> types() {
        return factories.keySet();
    }
}
