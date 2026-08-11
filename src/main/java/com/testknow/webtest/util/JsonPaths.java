package com.testknow.webtest.util;

import com.jayway.jsonpath.JsonPath;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 编译后 JsonPath 的全局缓存。编译开销大，压测热路径必须复用（线程安全）。
 */
public final class JsonPaths {

    private static final ConcurrentHashMap<String, JsonPath> CACHE = new ConcurrentHashMap<>();

    private JsonPaths() {
    }

    public static JsonPath compile(String expr) {
        return CACHE.computeIfAbsent(expr, JsonPath::compile);
    }
}
