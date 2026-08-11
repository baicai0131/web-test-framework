package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据驱动配置：用 CSV 每行数据跑一次引用的用例。
 *
 * <pre>
 * - test: create-order
 *   file: data/orders.csv
 *   bind:
 *     - { name: orderId, column: orderId }    # 列 → 变量
 *   mode: each                                 # 目前仅 each（每行一次）
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSetConfig {

    private String test;
    private String file;
    private List<BindColumn> bind = new ArrayList<>();
    private String mode = "each";

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public List<BindColumn> getBind() {
        return bind;
    }

    public void setBind(List<BindColumn> bind) {
        this.bind = bind;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /** CSV 列与变量的绑定。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BindColumn {
        private String name;
        private String column;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }
    }
}
