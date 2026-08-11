package com.testknow.webtest.config;

/**
 * 配置错误。抛出时携带清晰的、面向用户的错误信息，
 * 由 CLI 捕获后映射为 {@link com.testknow.webtest.cli.ExitCodes#CONFIG_ERROR}。
 */
public class ConfigError extends RuntimeException {

    public ConfigError(String message) {
        super(message);
    }

    public ConfigError(String message, Throwable cause) {
        super(message, cause);
    }
}
