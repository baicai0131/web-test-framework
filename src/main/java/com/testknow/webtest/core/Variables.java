package com.testknow.webtest.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不可变的作用域变量链。
 * 每个测试/会话通过 {@link #child} 派生新作用域，父作用域只读——
 * 天然线程安全，为后续性能引擎的 per-VU 会话隔离打基础。
 *
 * 解析顺序：当前作用域 → 父作用域 → … → 根（配置变量）。
 */
public final class Variables {

    private static final Variables EMPTY = new Variables(Map.of(), null);

    private final Map<String, String> local;
    private final Variables parent;

    private Variables(Map<String, String> local, Variables parent) {
        this.local = local == null ? Map.of() : local;
        this.parent = parent;
    }

    public static Variables root(Map<String, String> base) {
        if (base == null || base.isEmpty()) {
            return EMPTY;
        }
        return new Variables(base, null);
    }

    public static Variables empty() {
        return EMPTY;
    }

    /** 派生一个携带新增变量的子作用域。新增为空时返回 this。 */
    public Variables child(Map<String, String> additions) {
        if (additions == null || additions.isEmpty()) {
            return this;
        }
        return new Variables(additions, this);
    }

    /** 沿作用域链查找。未找到返回 null。 */
    public String get(String key) {
        if (local.containsKey(key)) {
            return local.get(key);
        }
        return parent == null ? null : parent.get(key);
    }

    public boolean contains(String key) {
        return local.containsKey(key) || (parent != null && parent.contains(key));
    }

    /** 展平为单层视图（调试 / 报告用），近层覆盖远层。 */
    public Map<String, String> flatten() {
        Map<String, String> merged = parent == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(parent.flatten());
        merged.putAll(local);
        return merged;
    }
}
