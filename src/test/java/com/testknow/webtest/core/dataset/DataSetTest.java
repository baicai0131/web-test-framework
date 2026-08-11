package com.testknow.webtest.core.dataset;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigLoader;
import com.testknow.webtest.config.ConfigValidator;
import com.testknow.webtest.config.model.ProjectConfig;
import com.testknow.webtest.core.TestRunner;
import com.testknow.webtest.core.result.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSetTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tmp;

    @Test
    void csvRowsRunTestPerRowWithBoundVars() throws Exception {
        wm.stubFor(post("/echo").willReturn(okJson("{\"received\":{\"name\":\"x\",\"qty\":\"y\"}}")));

        String csv = "itemName,itemQty\napple,3\n\"banana,large\",5\n";
        Path csvFile = tmp.resolve("items.csv");
        Files.writeString(csvFile, csv);

        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - name: create-item
                    method: POST
                    path: /echo
                    contentType: json
                    body: { item: "${itemName}", qty: "${itemQty}" }
                    asserts:
                      - { type: status, expected: 200 }
                dataSets:
                  - test: create-item
                    file: %s
                    bind:
                      - { name: itemName, column: itemName }
                      - { name: itemQty, column: itemQty }
                """.formatted("http://localhost:" + wm.getPort(),
                "items.csv"); // 相对路径基于配置文件所在目录 = tmp

        Path cfgFile = tmp.resolve("ds.yaml");
        Files.writeString(cfgFile, yaml);

        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        new ConfigValidator(new AssertionRegistry()).validate(cfg);
        ExecutionResult result = new TestRunner(cfg, null, tmp).run();

        // 1 个普通测试 + 2 行数据驱动
        assertEquals(3, result.getTotal());
        assertEquals(3, result.getPassed());
        // CSV 行名带后缀
        assertTrue(result.getCases().stream().anyMatch(c -> c.getName().contains("行2")));
        assertTrue(result.getCases().stream().anyMatch(c -> c.getName().contains("行1")));

        // 1 次普通用例 + 2 行数据驱动 = 3 次 POST
        wm.verify(3, postRequestedFor(urlEqualTo("/echo")));
    }

    @Test
    void assertionExpectedUsesBoundVariable() throws Exception {
        wm.stubFor(post("/echo").willReturn(okJson("{\"received\":{\"item\":\"apple\"}}")));
        Files.writeString(tmp.resolve("items.csv"), "itemName\napple\npear\n");

        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - name: create-item
                    method: POST
                    path: /echo
                    contentType: json
                    body: { item: "${itemName}" }
                    asserts:
                      - { type: status, expected: 200 }
                      - { type: jsonpath, expr: "$.received.item", equals: "${itemName}" }
                dataSets:
                  - test: create-item
                    file: items.csv
                    bind:
                      - { name: itemName, column: itemName }
                """.formatted("http://localhost:" + wm.getPort());

        Path cfgFile = tmp.resolve("ds2.yaml");
        Files.writeString(cfgFile, yaml);
        ProjectConfig cfg = new ConfigLoader().load(cfgFile);
        new ConfigValidator(new AssertionRegistry()).validate(cfg);
        ExecutionResult result = new TestRunner(cfg, null, tmp).run();

        // 1 个普通用例 + 2 行数据驱动 = 3；apple 行通过；pear 行因 echo 固定返回 apple 而断言失败
        assertEquals(3, result.getTotal());
        assertEquals(1, result.getPassed());
        assertEquals(2, result.getFailed());
    }
}
