# 待办清单（TODO）

> 本文件用于跨 session 继续开发。新会话开始先读本文件 + README，即可快速接上。

---

## 当前项目状态

- **位置**：`D:\javacode\self_code\testKnow\实战项目\web-test-framework`
- **GitHub**：https://github.com/baicai0131/web-test-framework （Public，CI 徽章绿）
- **已完成**：
  - Phase A：功能测试引擎（YAML 配置 / OkHttp / 断言 SPI / 退出码门禁）
  - Phase B：变量插值 / 环境切换 / 登录鉴权(token 关联) / 数据驱动 CSV
  - Phase D：性能引擎（虚拟线程压测 / TPS / P50-P999 / 阈值门禁 / per-VU 会话）
- **自测**：60 个测试全绿（`mvn test`，WireMock 本地 mock 后端）
- **最近提交**：`52291ad fix: 恢复 LoadGenerator 缺失的 Map import`

### 构建环境注意（Windows + Git Bash）

```bash
export JAVA_HOME="/c/Users/86188/.jdks/ms-21.0.8" && export PATH="$JAVA_HOME/bin:$PATH"
mvn test                              # 全量测试
mvn -DskipTests package               # 打 fat-jar → target/webtest.jar
java -jar target/webtest.jar run  -c config/demo.yaml   # 功能测试
java -jar target/webtest.jar perf -c config/demo.yaml   # 性能测试
```

> 注意：Maven 本地仓库在 `D:\repo`；阿里云镜像无 HdrHistogram 2.2.0（用 2.2.2）。

---

## 进行中 / 最近工作

- ✅ **Phase D 性能引擎**（完成并发布）
- 提交前**务必先 `mvn test`**（曾因删 import 未编译就提交，CI 编译失败，见 52291ad 修复）

---

## 待做任务（按优先级）

### 🔜 Phase C：断言/提取插件 SPI + HTML 报告

**目标**：插件化 + 可读报告。

- [ ] **断言/提取插件 SPI（ServiceLoader）**
  - 现状：`AssertionRegistry` / `ExtractorRegistry` 是硬编码注册内置类型
  - 改造：用 `ServiceLoader` + `META-INF/services` 发现自定义插件
  - 参考：`src/main/java/com/testknow/webtest/assertion/AssertionRegistry.java`、`core/extract/ExtractorRegistry.java`
  - 依赖：`com.google.auto.service`（@AutoService 免手写 services 文件）
  - 测试：构造一个自定义断言 jar 依赖，验证发现并执行

- [ ] **HTML 报告（带曲线图）**
  - 现状：只有 result.json + perf-result.json + 控制台汇总
  - 目标：FreeMarker 模板 `report.ftl` + 内嵌 uPlot（~40KB min，离线可用）画 TPS/RT 曲线
  - 数据来源：`perf-result.json` 的 `series` 数组（每秒 TimeSeriesPoint）
  - 报告结构：概要卡片 / 功能用例表（失败展开差异）/ 性能曲线+汇总表 / 安全区块(预留)
  - 注意：断言里响应体片段会进 HTML，FreeMarker 必须转义（防注入）

### 🔜 Phase E：稳定性压测 + 安全检查

- [ ] **稳定性压测（soak）**
  - 现状：`durationSec` 可配长时间，但无专门模式
  - 加：`leakWatchdog`（连接池 / 线程数 / 内存增长哨兵）
  - 加：资源曲线报告（与时间序列联动）
- [ ] **安全检查模块**
  - 现状：退出码 4 已预留但未实现
  - 加：`security:` 配置块（expectedHeaders / forbiddenHeaders / TLS 版本）
  - 复用断言层 HeaderAssertion 的能力，别另起炉灶
  - 用 uPlot 图表时注意 TPS/RT 双 Y 轴

### 🔜 Phase F：工程化收尾

- [ ] **离线报告回放**：`webtest report result.json` 只出 HTML，不重跑（依赖 result.json 的 schemaVersion）
- [ ] **多站点配置示例**：一套测试 + 多个 site 配置文件切换
- [ ] **更丰富的 ramp-up**：Step/Instant 之外加 RampUpProfile 策略（已留接口）
- [ ] **CI 可选 demo workflow**：对 postman-echo.com 的 demo 压测做成手动/定时触发的可选 job（不进 push CI，依赖公网不稳定）

### 🧹 技术债 / 优化

- [ ] `weight` 字段在 `PerfScenarioConfig` 中目前仅文档用途，未真正按权重分配并发（混合场景目前是各场景独立 users）——若需要按权重比例，需在 LoadGenerator 里做归一化
- [ ] `MetricsCollector.sample()` 用 `snapshotIntervalHistogram()` 会重置窗口，已通过合并到 `cumulative` 解决聚合丢数据；时间序列的 avg/p95 反映的是上一秒区间，语义需在报告里注明
- [ ] Console 中文在 Windows 终端乱码（文件是 UTF-8），可考虑 `chcp 65001` 说明或 ASCII 化输出
- [ ] 断言里 `rtMs` 在压测场景被排除在 ok 判定外（Worker 按状态码判定），行为需文档化

---

## 关键架构速览（新会话必读）

```
src/main/java/com/testknow/webtest/
├── Main.java / cli/          run(功能) / perf(性能) 子命令 + 退出码契约(0/1/2/3/4/5)
├── config/                   模型 + ConfigLoader + ConfigValidator
├── core/
│   ├── Variables / PlaceholderResolver   不可变作用域变量链 + ${env.X}/内置函数插值
│   ├── TestRuntime           环境解析 + 生效站点 + 根变量（功能/性能共用）
│   ├── auth/                 AuthManager + AuthSession（per-VU 登录）
│   ├── extract/              提取器 SPI + jsonpath/regex/header/body
│   ├── dataset/              CSV 读取 + 数据驱动
│   └── CaseExecutor / TestRunner
├── http/                     OkHttp 封装（超时/重试/鉴权头注入）
├── assertion/                断言 SPI + 5 内置断言
├── perf/                     性能引擎（LoadGenerator/Worker/metrics/ThresholdVerifier）
└── report/                   功能/性能结果 JSON + 控制台汇总
```

**关键设计点**：
- 换网站 = 改 `config/demo.yaml` 的 `baseUrl` + tests，代码零改动
- 退出码门禁：0 全过 / 1 功能失败 / 2 性能违规 / 3 配置错误 / 4 安全失败(预留) / 5 内部错误
- 压测线程模型：Java 21 虚拟线程承载 VU，per-VU 独立 AuthSession

---

## 测试覆盖现状（60 个）

| 测试类 | 覆盖 |
|--------|------|
| AssertionTest (9) | 断言通过/失败 + 差异信息 |
| ConfigLoaderTest (7) | 配置加载/校验/错误 |
| EnvironmentFlowTest (5) | 环境切换/变量/配置错误 |
| PlaceholderResolverTest (7) | 插值/内置函数/循环检测 |
| ExtractorTest (7) | 四种提取器 |
| AuthManagerTest (4) | 三种鉴权/401 重登 |
| DataSetTest (2) | CSV 数据驱动 |
| TestRunnerTest (2) | 端到端计数 |
| HttpCallerTest (4) | 方法/重试/超时 |
| LoadGeneratorTest (6) | 压测/迭代停止/错误率/阈值/会话隔离 |
| RunCommandTest (4) / PerfCommandTest (3) | CLI 退出码 |

---

## 提交约定

- 提交信息用 Conventional Commits（`feat:` / `fix:` / `ci:` / `docs:`）
- 每阶段：`mvn test` 全绿 → 打 fat-jar → demo 端到端 → 提交推送 → 等 CI 绿
- 发布后同步更新 README 的路线图（把已完成的 Phase 从待做挪到已完成）
