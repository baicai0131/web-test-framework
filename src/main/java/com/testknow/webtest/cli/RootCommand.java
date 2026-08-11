package com.testknow.webtest.cli;

import picocli.CommandLine.Command;

/**
 * 顶层命令，注册子命令：
 * <pre>webtest run -c &lt;config.yaml&gt;</pre>
 */
@Command(
        name = "webtest",
        mixinStandardHelpOptions = true,
        version = "web-test-framework 0.1.0",
        description = "配置驱动的通用 Web 测试框架",
        subcommands = {RunCommand.class}
)
public class RootCommand implements Runnable {

    @Override
    public void run() {
        // 无子命令时打印帮助
        new picocli.CommandLine(this).usage(System.out);
    }
}
