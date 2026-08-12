package com.testknow.webtest.cli;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * perf 子命令退出码门禁：0=门禁通过，2=阈值违规，3=配置错误。
 */
class PerfCommandTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @TempDir
    Path tmp;

    private int run(String yaml, Path out) throws Exception {
        Path cfg = tmp.resolve("cfg.yaml");
        Files.writeString(cfg, yaml);
        CommandLine cmd = new CommandLine(new PerfCommand());
        return cmd.execute("-c", cfg.toString(), "-o", out.toString());
    }

    @Test
    void exitZeroWhenThresholdsPass() throws Exception {
        wm.stubFor(get("/ping").willReturn(ok("pong")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: ping, path: /ping }
                performance:
                  - name: ok
                    durationSec: 1
                    scenarios:
                      - { ref: ping, users: 3 }
                """.formatted("http://localhost:" + wm.getPort());
        Path out = tmp.resolve("out-ok");
        assertEquals(ExitCodes.OK, run(yaml, out));
        assertTrue(Files.exists(out.resolve("perf-result.json")));
    }

    @Test
    void exitTwoWhenThresholdViolated() throws Exception {
        wm.stubFor(get("/ping").willReturn(ok("pong")));
        String yaml = """
                site:
                  baseUrl: %s
                tests:
                  - { name: ping, path: /ping }
                performance:
                  - name: fail
                    durationSec: 1
                    scenarios:
                      - { ref: ping, users: 2 }
                    thresholds:
                      tpsMin: 999999
                """.formatted("http://localhost:" + wm.getPort());
        assertEquals(ExitCodes.PERF_THRESHOLD, run(yaml, tmp.resolve("out-fail")));
    }

    @Test
    void exitThreeOnConfigError() throws Exception {
        String yaml = """
                site:
                  baseUrl: http://localhost:1
                tests:
                  - { name: ping, path: /ping }
                performance:
                  - name: bad
                    durationSec: 1
                    scenarios:
                      - { ref: nope, users: 2 }
                """;
        assertEquals(ExitCodes.CONFIG_ERROR, run(yaml, tmp.resolve("out-bad")));
    }
}
