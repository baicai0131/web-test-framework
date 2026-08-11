package com.testknow.webtest.core.auth;

import com.testknow.webtest.assertion.AssertContext;
import com.testknow.webtest.assertion.Assertion;
import com.testknow.webtest.assertion.AssertionFailure;
import com.testknow.webtest.assertion.AssertionRegistry;
import com.testknow.webtest.config.ConfigError;
import com.testknow.webtest.config.model.AuthConfig;
import com.testknow.webtest.config.model.TestCaseConfig;
import com.testknow.webtest.core.PlaceholderResolver;
import com.testknow.webtest.core.Variables;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 鉴权管理器。
 *
 * <ul>
 *   <li>bearerToken / basic：静态鉴权头（token 可引用 ${env.X} 从环境注入）</li>
 *   <li>login：先跑登录请求，从响应提取变量（如 token），再按 injectHeader 模板生成请求头</li>
 *   <li>retryOn401：收到 401 时重新登录/刷新后由执行器重试一次</li>
 * </ul>
 */
public class AuthManager {

    private static final Logger log = LoggerFactory.getLogger(AuthManager.class);

    private final AuthConfig auth;
    private final HttpCaller caller;
    private final RequestBuilder requestBuilder;   // 登录请求专用（不注入鉴权头）
    private final AssertionRegistry assertions;
    private final ExtractorRegistry extractors;
    private final Variables baseVars;
    private final PlaceholderResolver resolver = PlaceholderResolver.INSTANCE;

    private Map<String, String> headers = Map.of();

    public AuthManager(AuthConfig auth, HttpCaller caller, RequestBuilder requestBuilder,
                       AssertionRegistry assertions, ExtractorRegistry extractors, Variables baseVars) {
        this.auth = auth == null ? new AuthConfig() : auth;
        this.caller = caller;
        this.requestBuilder = requestBuilder;
        this.assertions = assertions;
        this.extractors = extractors;
        this.baseVars = baseVars == null ? Variables.empty() : baseVars;
    }

    public boolean isEnabled() {
        return auth.getType() != null && !"none".equalsIgnoreCase(auth.getType());
    }

    public boolean retryOn401() {
        return isEnabled() && auth.isRetryOn401();
    }

    /** 是否对指定用例注入鉴权头。applyTo 为空 = 全部。 */
    public boolean appliesTo(String testName) {
        if (!isEnabled()) {
            return false;
        }
        List<String> applyTo = auth.getApplyTo();
        return applyTo == null || applyTo.isEmpty() || applyTo.contains(testName);
    }

    public Map<String, String> headers() {
        return headers;
    }

    /**
     * 初始化鉴权（登录/静态头）。返回登录流程的用例结果（静态类型返回 null）。
     */
    public CaseResult login() {
        if (!isEnabled()) {
            return null;
        }
        return switch (auth.getType().toLowerCase()) {
            case "bearertoken" -> {
                String token = resolver.resolve(auth.getToken(), baseVars);
                headers = Map.of("Authorization", "Bearer " + token);
                yield null;
            }
            case "basic" -> {
                String user = resolver.resolve(auth.getUsername(), baseVars);
                String pass = resolver.resolve(auth.getPassword(), baseVars);
                String cred = user + ":" + pass;
                headers = Map.of("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8)));
                yield null;
            }
            case "login" -> doLogin();
            default -> throw new ConfigError("不支持的 auth.type: '" + auth.getType()
                    + "'，可选: none | bearerToken | basic | login");
        };
    }

    /**
     * 重新登录（401 重试用）。静态类型无需刷新。
     */
    public void refresh() {
        if (!isEnabled() || !"login".equalsIgnoreCase(auth.getType())) {
            return;
        }
        doLogin();
    }

    private CaseResult doLogin() {
        TestCaseConfig login = auth.getLogin();
        if (login == null) {
            throw new ConfigError("auth.type=login 需要配置 auth.login");
        }
        try {
            Request req = requestBuilder.build(login, baseVars, Map.of());
            ResponseEnvelope resp = caller.execute(req);

            List<AssertionFailure> failures = new ArrayList<>();
            for (Map<String, Object> spec : login.getAsserts()) {
                Assertion assertion = assertions.parse(spec);
                Optional<AssertionFailure> failure = assertion.evaluate(new AssertContext(resp, baseVars));
                failure.ifPresent(failures::add);
            }

            Map<String, String> extracted = Extractors.extract(login.getExtract(), resp, baseVars, extractors);
            Variables authVars = baseVars.child(extracted);

            Map<String, String> h = new LinkedHashMap<>();
            auth.getInjectHeader().forEach((k, v) -> h.put(k, resolver.resolve(v, authVars)));
            headers = h;

            log.info("登录成功: {} {} → {} (提取变量: {})",
                    login.getMethod(), login.getPath(), resp.code(), extracted.keySet());
            return new CaseResult("auth.login", login.getMethod(), login.getPath(),
                    resp.code(), resp.elapsedNanos(), failures, null, extracted);
        } catch (IOException e) {
            headers = Map.of();
            log.error("登录失败(网络): {} {}", login.getMethod(), login.getPath(), e);
            return new CaseResult("auth.login", login.getMethod(), login.getPath(),
                    null, 0, List.of(), "登录失败(网络): " + e.getMessage(), Map.of());
        }
    }
}
