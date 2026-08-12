package com.testknow.webtest.perf;

/**
 * ramp-up 分布：第 i 个用户（0-based）应延迟多久启动，使全部用户随时间均匀拉满。
 */
public final class RampUpProfile {

    /** 线性 ramp-up。rampUpSec=0 或 users<=1 时返回 0（瞬时启动）。 */
    public static long delayNanos(int index, int users, int rampUpSec) {
        if (rampUpSec <= 0 || users <= 1) {
            return 0;
        }
        return (long) index * rampUpSec * 1_000_000_000L / users;
    }
}
