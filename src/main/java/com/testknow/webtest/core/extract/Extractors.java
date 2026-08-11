package com.testknow.webtest.core.extract;

import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.http.ResponseEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 批量执行 extract 配置的工具方法。
 */
public final class Extractors {

    private static final Logger log = LoggerFactory.getLogger(Extractors.class);

    private Extractors() {
    }

    /**
     * 对一次响应依次执行所有提取器，返回 变量名 → 值。
     * 未命中的变量跳过并告警。
     */
    public static Map<String, String> extract(List<Map<String, Object>> specs, ResponseEnvelope resp,
                                              Variables vars, ExtractorRegistry registry) {
        Map<String, String> out = new LinkedHashMap<>();
        if (specs == null || specs.isEmpty()) {
            return out;
        }
        for (Map<String, Object> spec : specs) {
            Object nameObj = spec.get("name");
            if (nameObj == null || String.valueOf(nameObj).isBlank()) {
                throw new ConfigError("extract 配置缺少 name 字段: " + spec);
            }
            String name = String.valueOf(nameObj);
            Extractor extractor = registry.parse(spec);
            Optional<String> value = extractor.extract(resp, vars);
            if (value.isPresent()) {
                out.put(name, value.get());
            } else {
                log.warn("提取变量 '{}' 未命中: {}", name, extractor.describe());
            }
        }
        return out;
    }
}
