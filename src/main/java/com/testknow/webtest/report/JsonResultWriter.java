package com.testknow.webtest.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testknow.webtest.core.result.CaseResult;
import com.testknow.webtest.core.result.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将执行结果写入 result.json（带 schemaVersion，供后续离线回放）。
 */
public class JsonResultWriter {

    public static final int SCHEMA_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger(JsonResultWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Path write(ExecutionResult result, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
            Path file = outputDir.resolve("result.json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), toMap(result));
            log.info("结果已写入: {}", file.toAbsolutePath());
            return file;
        } catch (IOException e) {
            throw new RuntimeException("写入结果文件失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> toMap(ExecutionResult result) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("tool", "web-test-framework");
        root.put("siteName", result.getSiteName());
        if (result.getEnvironmentName() != null) {
            root.put("environment", result.getEnvironmentName());
        }
        root.put("startTime", Instant.ofEpochMilli(result.getStartEpochMillis()).toString());
        root.put("elapsedMillis", result.getTotalElapsedMillis());
        root.put("summary", Map.of(
                "total", result.getTotal(),
                "passed", result.getPassed(),
                "failed", result.getFailed()));

        List<Map<String, Object>> cases = new ArrayList<>();
        for (CaseResult c : result.getCases()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("name", c.getName());
            cm.put("method", c.getMethod());
            cm.put("path", c.getPath());
            cm.put("status", c.getStatusCode());
            cm.put("elapsedMillis", c.getElapsedMillis());
            cm.put("pass", c.isPass());
            if (c.getErrorMessage() != null) {
                cm.put("error", c.getErrorMessage());
            }
            List<Map<String, Object>> failures = new ArrayList<>();
            c.getFailures().forEach(f -> failures.add(Map.of(
                    "check", f.check(),
                    "expected", f.expected(),
                    "actual", f.actual(),
                    "path", String.valueOf(f.path()))));
            cm.put("failures", failures);
            if (!c.getExtractedVars().isEmpty()) {
                cm.put("extracted", c.getExtractedVars());
            }
            cases.add(cm);
        }
        root.put("cases", cases);
        return root;
    }
}
