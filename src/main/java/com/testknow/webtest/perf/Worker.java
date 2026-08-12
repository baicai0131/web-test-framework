package com.testknow.webtest.perf;

import com.testknow.webtest.config.model.PerfConfig;
import com.testknow.webtest.config.model.PerfScenarioConfig;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.CaseExecutor;
import com.testknow.webtest.core.Variables;
import com.testknow.webtest.core.auth.AuthManager;
import com.testknow.webtest.core.auth.AuthSession;
import com.testknow.webtest.core.result.CaseResult;
import com.testknow.webtest.perf.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 一个虚拟用户（一个虚拟线程）：
 * 首次迭代前建立 per-VU 会话（若配置了 login 鉴权则独立登录拿到自己的 token），
 * 然后循环执行场景引用的用例，直到停止条件满足。
 */
final class Worker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final PerfConfig plan;
    private final PerfScenarioConfig scenario;
    private final TestCaseConfig testCase;
    private final CaseExecutor executor;
    private final AuthManager auth;
    private final Variables rootVars;
    private final MetricsCollector metrics;
    private final StopStrategy stop;
    private final String vuId;

    Worker(PerfConfig plan, PerfScenarioConfig scenario, TestCaseConfig testCase,
           CaseExecutor executor, AuthManager auth, Variables rootVars,
           MetricsCollector metrics, StopStrategy stop, String vuId) {
        this.plan = plan;
        this.scenario = scenario;
        this.testCase = testCase;
        this.executor = executor;
        this.auth = auth;
        this.rootVars = rootVars;
        this.metrics = metrics;
        this.stop = stop;
        this.vuId = vuId;
    }

    @Override
    public void run() {
        // per-VU 会话：独立登录（若 login 鉴权），否则用静态/无鉴权
        AuthSession session = auth == null ? AuthSession.NONE : auth.createSession();
        Variables vars = rootVars;
        if (session.vars() != null && session.vars() != rootVars) {
            vars = session.vars();
        }
        Map<String, String> authHeaders = session.headers();
        if (authHeaders == null) {
            authHeaders = Map.of();
        }
        log.debug("虚拟用户 {} 会话就绪: 鉴权头 {}", vuId,
                authHeaders.isEmpty() ? "(无)" : authHeaders.keySet());

        int remaining = plan.usesDuration() ? Integer.MAX_VALUE
                : ((StopStrategy.IterationBased) stop).iterations();

        while (!Thread.currentThread().isInterrupted()
                && remaining-- > 0
                && !stop.isExpired(System.nanoTime())) {

            long t0 = System.nanoTime();
            CaseResult result = executor.execute(testCase, vars, authHeaders, null);
            long latencyNanos = System.nanoTime() - t0;

            // 压测错误率以状态码为准（网络错误 / 5xx 计为失败），
            // 避免用例断言（如 rtMs 上限）把错误率虚高
            boolean ok = result.getStatusCode() != null && result.getStatusCode() < 500;
            metrics.record(scenario.getRef(), ok, latencyNanos);

            if (scenario.getThinkTimeMs() > 0) {
                sleepQuietly(scenario.getThinkTimeMs());
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
