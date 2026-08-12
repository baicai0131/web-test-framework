package com.testknow.webtest.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 单个性能场景：引用一个功能用例（ref），以固定虚拟用户数 + think time 循环执行。
 *
 * <pre>
 * - { ref: get-json, weight: 60, users: 30, rampUpSec: 20, thinkTimeMs: 200 }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerfScenarioConfig {

    private String ref;            // 引用 tests 中已定义的用例名
    private int weight = 1;        // 混合场景权重（不同用户数分配，MVP 保留用于文档）
    private int users = 1;         // 并发虚拟用户数
    private int rampUpSec = 0;     // ramp-up 到全部用户的时间（秒），0=瞬时
    private long thinkTimeMs = 0;  // 每次迭代后的思考时间（毫秒）

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getUsers() {
        return users;
    }

    public void setUsers(int users) {
        this.users = users;
    }

    public int getRampUpSec() {
        return rampUpSec;
    }

    public void setRampUpSec(int rampUpSec) {
        this.rampUpSec = rampUpSec;
    }

    public long getThinkTimeMs() {
        return thinkTimeMs;
    }

    public void setThinkTimeMs(long thinkTimeMs) {
        this.thinkTimeMs = thinkTimeMs;
    }
}
