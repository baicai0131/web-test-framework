package com.testknow.webtest.assertion;

import java.util.Optional;

/**
 * 断言 SPI。返回空表示通过；返回 {@link AssertionFailure} 表示失败，失败信息携带结构化差异。
 * 自定义断言实现此接口后注册进 {@link AssertionRegistry}（后续阶段支持 ServiceLoader 插件发现）。
 */
public interface Assertion {

    /**
     * 对一次请求结果执行断言。
     *
     * @return 空 Optional = 通过；非空 = 失败详情
     */
    Optional<AssertionFailure> evaluate(AssertContext ctx);

    /** 人类可读描述，用于报告展示。 */
    String describe();
}
