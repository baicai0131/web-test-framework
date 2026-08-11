package com.testknow.webtest.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;

/**
 * CLI 退出码门禁：0=全过，1=功能失败，3=配置错误。
 */
class RunCommandTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tmp;

    private int run(String yaml, Path outputDir) throws Exception {
        Path cfgFile = tmp.resolve("cfg.yaml");
        Files.writeString(cfgFile, yaml);
        CommandLine cmd = new CommandLine(new RunCommand());
        if (outputDir == null) {
            return cmd.execute("-c", cfgFile.toString());
        }
        return cmd.execute("-c", cfgFile.toString(), "-o", outputDir.toString());
    }

    @Test
    void exitZeroWhenAllPass() throws Exception {
        wm.stubFor(get("/ok").willReturn(ok("fine")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - name: ok
                    path: /ok
                    asserts:
                      - { type: status, expected: 200 }
                """.formatted("http://localhost:" + wm.getPort());
        Path out = tmp.resolve("out-ok");
        assertEquals(ExitCodes.OK, run(yaml, out));
        assertTrue(Files.exists(out.resolve("result.json")));
    }

    @Test
    void exitOneWhenAssertionFails() throws Exception {
        wm.stubFor(get("/ok").willReturn(ok("fine")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - name: expect-500
                    path: /ok
                    asserts:
                      - { type: status, expected: 500 }
                """.formatted("http://localhost:" + wm.getPort());
        assertEquals(ExitCodes.FUNCTIONAL_FAILURE, run(yaml, tmp.resolve("out-fail")));
    }

    @Test
    void exitThreeOnConfigError() throws Exception {
        String yaml = """
                tests:
                  - name: no-site
                    path: /
                """;
        assertEquals(ExitCodes.CONFIG_ERROR, run(yaml, tmp.resolve("out-bad")));
    }

    @Test
    void missingConfigFileReturnsThree() {
        CommandLine cmd = new CommandLine(new RunCommand());
        assertEquals(ExitCodes.CONFIG_ERROR,
                cmd.execute("-c", tmp.resolve("not-exist.yaml").toString()));
    }
}
