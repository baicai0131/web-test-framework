package com.testknow.webtest.perf;

import com.testknow.webtest.config.model.PerfConfig;
import com.testknow.webtest.config.model.PerfScenarioConfig;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.CaseExecutor;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.auth.AuthManager;
import com.testknow.webtest.perf.metrics.MetricsCollector;
import com.testknow.webtest.perf.metrics.TimeSeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 * 压测生成器：为每个场景启动若干虚拟线程（虚拟用户），
 * 主循环按 tick 采样时间序列，满足停止条件后 interrupt + join 收尾并聚合。
 *
 * 线程模型：Java 21 虚拟线程承载每个 VU（阻塞式 execute 成本极低）；
 * 并发上限由场景 users 决定，不无界 spawn。
 */
public class LoadGenerator {

    private static final Logger log = LoggerFactory.getLogger(LoadGenerator.class);
    private static final long TICK_MILLIS = 1000;

    private final PerfConfig plan;
    private final Function<String, TestCaseConfig> testResolver;
    private final CaseExecutor executor;
    private final AuthManager auth;
    private final Variables rootVars;
    private final MetricsCollector metrics = new MetricsCollector();

    public LoadGenerator(PerfConfig plan, Function<String, TestCaseConfig> testResolver,
                         CaseExecutor executor, AuthManager auth, Variables rootVars) {
        this.plan = plan;
        this.testResolver = testResolver;
        this.executor = executor;
        this.auth = auth;
        this.rootVars = rootVars;
    }

    /**
     * 运行压测，返回 (场景名 → 聚合指标) 与时间序列。
     */
    public PerfRunResult run() {
        long startedNanos = System.nanoTime();
        long startedEpoch = startedNanos / 1_000_000_000L;
        metrics.start(startedEpoch);

        StopStrategy stop = StopStrategy.of(plan);
        List<Thread> workers = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(totalUsers());

        log.info("压测开始 [{}]: {} 个场景，共 {} 个虚拟用户, 停止条件={}",
                plan.getName(), plan.getScenarios().size(), totalUsers(),
                plan.usesDuration() ? "时长 " + plan.getDurationSec() + "s"
                        : "迭代 " + plan.getIterations() + " 次/用户");

        for (PerfScenarioConfig sc : plan.getScenarios()) {
            TestCaseConfig tc = testResolver.apply(sc.getRef());
            if (tc == null) {
                throw new IllegalArgumentException("性能场景引用的用例不存在: " + sc.getRef());
            }
            for (int i = 0; i < sc.getUsers(); i++) {
                long delay = RampUpProfile.delayNanos(i, sc.getUsers(), sc.getRampUpSec());
                String vuId = sc.getRef() + "-" + i;
                Thread t = Thread.ofVirtual().name("vu-" + vuId).start(() -> {
                    sleepNanosQuietly(delay);
                    try {
                        new Worker(plan, sc, tc, executor, auth, rootVars, metrics, stop, vuId).run();
                    } finally {
                        latch.countDown();
                    }
                });
                workers.add(t);
            }
        }

        // 主循环：按 tick 采样，直到全部 worker 结束（duration 模式由 Worker 内部判断停止，
        // 主循环等 latch；迭代模式 worker 自然耗尽）
        long deadlineNanos = startedNanos + (plan.usesDuration()
                ? plan.getDurationSec() * 1_000_000_000L : Long.MAX_VALUE);
        while (latch.getCount() > 0) {
            // 采样
            long epochSecond = (System.nanoTime() - startedNanos) / 1_000_000_000L;
            metrics.sample(epochSecond);

            if (plan.usesDuration()) {
                if (System.nanoTime() >= deadlineNanos) {
                    break; // 时长到，中断收尾
                }
            } else {
                if (latch.getCount() == 0) {
                    break;
                }
            }
            try {
                Thread.sleep(TICK_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 收尾：中断所有仍在跑的 worker 并等待退出
        workers.forEach(Thread::interrupt);
        workers.forEach(w -> {
            try {
                w.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            latch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        Map<String, com.testknow.webtest.perf.metrics.PerfAggregate> aggregates = metrics.aggregate(elapsedNanos);
        List<TimeSeriesPoint> series = metrics.series();

        log.info("压测结束 [{}]: 耗时 {}ms, 场景聚合: {}", plan.getName(), elapsedNanos / 1_000_000,
                aggregates.keySet());
        return new PerfRunResult(plan.getName(), startedNanos, elapsedNanos, aggregates, series);
    }

    private int totalUsers() {
        int total = 0;
        for (PerfScenarioConfig sc : plan.getScenarios()) {
            total += sc.getUsers();
        }
        return total;
    }

    private static void sleepNanosQuietly(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
