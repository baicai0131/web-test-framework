package com.testknow.webtest;

import com.testknow.webtest.cli.ExitCodes;
import com.testknow.webtest.cli.RootCommand;
import picocli.CommandLine;

/**
 * 通用 Web 测试框架入口。
 * 用法：webtest run -c <config.yaml>
 */
public class Main {

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new RootCommand())
                .setCommandName("webtest")
                .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                    System.err.println("[内部错误] " + ex.getMessage());
                    ex.printStackTrace(System.err);
                    return ExitCodes.INTERNAL_ERROR;
                })
                .setParameterExceptionHandler((ex, ignoredArgs) -> {
                    System.err.println(ex.getMessage());
                    ex.getCommandLine().usage(System.err);
                    return ExitCodes.CONFIG_ERROR;
                });
        System.exit(cmd.execute(args));
    }
}
