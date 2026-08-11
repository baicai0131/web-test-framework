package com.testknow.webtest.core.extract;

import com.testknow.webtest.core.Variables;
import com.testknow.webtest.http.ResponseEnvelope;

import java.util.Optional;

/**
 * 变量提取 SPI。从一次响应中提取一个变量值。
 * 自定义提取器实现此接口并注册进 {@link ExtractorRegistry}。
 */
public interface Extractor {

    /** @return 提取到的值；未命中返回空 */
    Optional<String> extract(ResponseEnvelope resp, Variables vars);

    String describe();
}
