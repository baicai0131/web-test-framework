package com.testknow.webtest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.testknow.webtest.config.model.ProjectConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 从 YAML 文件加载 {@link ProjectConfig}。文件缺失/格式错误时抛出 {@link ConfigError}。
 */
public class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    public ProjectConfig load(Path file) {
        if (!Files.exists(file)) {
            throw new ConfigError("配置文件不存在: " + file.toAbsolutePath());
        }
        String yaml;
        try {
            yaml = Files.readString(file);
        } catch (IOException e) {
            throw new ConfigError("读取配置文件失败: " + file.toAbsolutePath() + " (" + e.getMessage() + ")", e);
        }
        try {
            return MAPPER.readValue(yaml, ProjectConfig.class);
        } catch (Exception e) {
            throw new ConfigError("YAML 解析失败: " + file.toAbsolutePath() + " → " + e.getMessage(), e);
        }
    }
}
