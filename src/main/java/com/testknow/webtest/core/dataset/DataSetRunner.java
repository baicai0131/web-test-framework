package com.testknow.webtest.core.dataset;

import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.model.DataSetConfig;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.CaseExecutor;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.auth.AuthManager;
import com.testknow.webtest.core.result.CaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据驱动执行器：CSV 每行绑定为变量，跑一次引用的用例。
 * CSV 文件路径相对于配置文件所在目录解析。
 */
public class DataSetRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSetRunner.class);

    private final CaseExecutor caseExecutor;
    private final Path baseDir;

    public DataSetRunner(CaseExecutor caseExecutor, Path baseDir) {
        this.caseExecutor = caseExecutor;
        this.baseDir = baseDir;
    }

    public List<CaseResult> run(DataSetConfig ds, TestCaseConfig test, Variables vars, AuthManager auth) {
        if (ds.getMode() != null && !"each".equalsIgnoreCase(ds.getMode())) {
            throw new ConfigError("不支持的 dataSets.mode: '" + ds.getMode() + "'，当前仅支持 each");
        }
        if (test == null) {
            throw new ConfigError("dataSets 引用了不存在的用例: " + ds.getTest());
        }
        Path file = baseDir.resolve(ds.getFile());
        List<Map<String, String>> rows = CsvReader.read(file);
        log.info("数据集 {} 共 {} 行，引用用例 {}", ds.getFile(), rows.size(), test.getName());

        List<CaseResult> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Variables rowVars = vars.child(bind(ds, rows.get(i)));
            CaseResult result = caseExecutor.execute(test, rowVars, auth);
            out.add(result.withName(result.getName() + " [行" + (i + 1) + "]"));
        }
        return out;
    }

    private Map<String, String> bind(DataSetConfig ds, Map<String, String> row) {
        Map<String, String> bound = new LinkedHashMap<>();
        for (DataSetConfig.BindColumn b : ds.getBind()) {
            String value = row.get(b.getColumn());
            if (value == null) {
                log.warn("CSV 缺少列 '{}'，变量 '{}' 置空", b.getColumn(), b.getName());
                value = "";
            }
            bound.put(b.getName(), value);
        }
        return bound;
    }
}
