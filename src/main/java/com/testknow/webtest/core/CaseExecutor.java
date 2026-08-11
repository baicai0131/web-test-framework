package com.testknow.webtest.core;

import com.testknow.webtest.assertion.AssertContext;
import com.testknow.webtest.assertion.Assertion;
import com.testknow.webtest.assertion.AssertionFailure;
import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.auth.AuthManager;
import com.testknow.webtest.core.extract.ExtractorRegistry;
import com.testknow.webtest.core.extract.Extractors;
import com.testknow.webtest.core.result.CaseResult;
import com.testknow.webtest.http.HttpCaller;
import com.testknow.webtest.http.RequestBuilder;
import com.testknow.webtest.http.ResponseEnvelope;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单用例执行器：构建请求 → 发送 → 401 重登重试 → 依次断言 → 提取变量 → 产出 {@link CaseResult}。
 * 断言失败不抛异常，全部收集；网络错误记录 errorMessage。
 */
public class CaseExecutor {

    private static final Logger log = LoggerFactory.getLogger(CaseExecutor.class);

    private final HttpCaller caller;
    private final RequestBuilder requestBuilder;
    private final AssertionRegistry assertions;
    private final ExtractorRegistry extractors;

    public CaseExecutor(HttpCaller caller, RequestBuilder requestBuilder,
                        AssertionRegistry assertions, ExtractorRegistry extractors) {
        this.caller = caller;
        this.requestBuilder = requestBuilder;
        this.assertions = assertions;
        this.extractors = extractors;
    }

    /**
     * 执行单个用例。
     *
     * @param auth 鉴权管理器（可空）；负责注入鉴权头与 401 重登
     */
    public CaseResult execute(TestCaseConfig tc, Variables vars, AuthManager auth) {
        try {
            Map<String, String> authHeaders = authHeadersFor(tc, auth);
            Request request = requestBuilder.build(tc, vars, authHeaders);
            ResponseEnvelope resp = caller.execute(request);

            if (resp.code() == 401 && auth != null && auth.retryOn401()) {
                log.warn("[{}] 收到 401，重新登录后重试一次", tc.getName());
                auth.refresh();
                request = requestBuilder.build(tc, vars, authHeadersFor(tc, auth));
                resp = caller.execute(request);
            }

            List<AssertionFailure> failures = evaluate(tc, resp, vars);

            Map<String, String> extracted = Extractors.extract(tc.getExtract(), resp, vars, extractors);
            return new CaseResult(tc.getName(), tc.getMethod(), tc.getPath(),
                    resp.code(), resp.elapsedNanos(), failures, null, extracted);
        } catch (IOException e) {
            log.error("请求失败 [{}]: {}", tc.getName(), e.getMessage());
            return new CaseResult(tc.getName(), tc.getMethod(), tc.getPath(),
                    null, 0, List.of(), "网络错误: " + e.getMessage(), Map.of());
        }
    }

    private List<AssertionFailure> evaluate(TestCaseConfig tc, ResponseEnvelope resp, Variables vars) {
        List<AssertionFailure> failures = new ArrayList<>();
        for (Map<String, Object> spec : tc.getAsserts()) {
            Assertion assertion = assertions.parse(spec);
            AssertContext ctx = new AssertContext(resp, vars);
            Optional<AssertionFailure> failure = assertion.evaluate(ctx);
            if (failure.isPresent()) {
                failures.add(failure.get());
                log.warn("断言失败 [{}]: {}", tc.getName(), failure.get().render());
            }
        }
        return failures;
    }

    private Map<String, String> authHeadersFor(TestCaseConfig tc, AuthManager auth) {
        if (auth != null && auth.appliesTo(tc.getName())) {
            return auth.headers();
        }
        return Map.of();
    }
}
