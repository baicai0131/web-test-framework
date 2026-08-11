# web-test-framework

配置驱动的**通用 Web 测试框架**。读一份 YAML 配置文件即可对任意网站执行功能测试——
**换被测网站只需改配置，代码零改动**。

已支持：功能测试 · 变量插值 · 环境切换 · 登录鉴权（token 关联）· 数据驱动 CSV。

## 快速开始

```bash
# 1. 构建（需 JDK 21，Maven）
export JAVA_HOME="/c/Users/86188/.jdks/ms-21.0.8"
mvn -DskipTests package

# 2. 对 postman-echo.com 跑完整演示（登录→拿token→带token调接口 + 数据驱动）
java -jar target/webtest.jar run -c config/demo.yaml

# 3. 结果输出
#    控制台汇总 + target/reports/result.json
```

### 用自己的网站

复制 `config/demo.yaml`，把 `environments.*.baseUrl`（或 `site.baseUrl`）换成你的站点，
在 `tests` 里写你的接口（`path` 只写相对路径）：

```yaml
site:
  name: my-site
  baseUrl: https://app.example.com     # ← 换成你的网站
  variables:
    ACCOUNT: demo@corp.com
tests:
  - name: 首页可达
    method: GET
    path: /
    asserts:
      - { type: status, expected: 200 }
```

## CLI 用法

```
webtest run -c <config.yaml> [-o <输出目录>] [-e <环境名>]
```

| 参数 | 说明 | 默认 |
|------|------|------|
| `-c, --config` | 配置文件路径（必填） | — |
| `-o, --output-dir` | 结果输出目录 | `target/reports` |
| `-e, --env` | 选择环境（覆盖配置中的 `env`） | 配置的 `env` |

## 退出码契约（CI 门禁）

| 退出码 | 含义 |
|--------|------|
| `0` | 全部用例通过 |
| `1` | 功能断言失败 |
| `2` | 性能阈值违规（预留） |
| `3` | 配置 / 参数错误 |
| `4` | 安全检查失败（预留） |
| `5` | 内部错误 |

## 配置参考

### site（被测站点）

| 字段 | 说明 |
|------|------|
| `name` | 站点名，用于报告 |
| `baseUrl` | 站点根地址（环境未覆盖时生效） |
| `globalHeaders` | 附加到每个请求的请求头（支持插值） |
| `variables` | 全局变量（支持 `${env.X}`、内置函数） |
| `timeouts.connect/read/write` | 连接/读/写超时（毫秒） |
| `retry.count` | 重试次数（仅网络失败 / 5xx 触发，断言失败不重试） |
| `retry.backoffMillis` | 重试间隔（毫秒，指数递增） |

### 环境切换

```yaml
env: test                    # 默认环境
environments:
  test:
    baseUrl: https://test.example.com
    variables: { ACCOUNT: "t@test.io" }
  prod:
    baseUrl: https://example.com
    variables:
      ACCOUNT: "real@corp.com"
      PASSWORD: "${env.ACCOUNT_PASSWORD}"   # 从 CI Secret 注入，不落盘
```

`--env prod` 覆盖 `env` 默认值；环境变量的 `baseUrl` 覆盖 `site.baseUrl`，变量与 `site.variables` 合并（环境优先）。

### tests（测试用例）

| 字段 | 说明 |
|------|------|
| `name` | 用例名（必填，全局唯一） |
| `method` | GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS，默认 GET |
| `path` | 相对路径，必须以 `/` 开头，支持插值 |
| `query` | 查询参数（`key: value`，值建议加引号，支持插值） |
| `headers` | 该用例专用请求头（支持插值） |
| `contentType` | body 的 Content-Type，默认 `application/json` |
| `body` | 请求体：YAML 对象（序列化为 JSON）或字符串（支持插值） |
| `asserts` | 断言列表 |
| `extract` | 变量提取列表（见下） |

### 变量提取与关联（token 传递）

```yaml
# 用例 A 提取 token
- name: login
  method: POST
  path: /api/auth/login
  body: { username: "${variables.ACCOUNT}", password: "${variables.PASSWORD}" }
  extract:
    - { name: token, type: jsonpath, expr: "$.data.access_token" }

# 用例 B 直接使用 ${token}（提取变量沿执行顺序向后传递）
- name: profile
  path: /api/users/me
  headers:
    Authorization: "Bearer ${token}"
  asserts:
    - { type: jsonpath, expr: "$.data.email", equals: "${variables.ACCOUNT}" }
```

提取类型：`jsonpath`（`expr`，列表取首元素）、`regex`（`pattern` + `group`，默认 1）、
`header`（`header` 名）、`body`（整段响应体）。

### 变量插值

| 写法 | 含义 |
|------|------|
| `${variables.X}` 或 `${X}` | 作用域变量（配置变量 / 提取变量 / 数据驱动行变量） |
| `${env.X}` | OS 环境变量 / 系统属性（CI Secret 注入） |
| `${random.uuid}` | 随机 UUID |
| `${timestamp.iso}` | ISO-8601 时间戳 |
| `${now.epochMilli}` | 当前毫秒时间戳 |

未解析的占位符保持字面量原样（便于排查），配置变量循环引用会报错。

### 登录鉴权（auth）

```yaml
auth:
  type: login            # none | bearerToken | basic | login
  # bearerToken:  token: "xxx" 或 token: "${env.TOKEN}"
  # basic:        username/password
  login:                 # type=login 时：登录请求（复用 tests 的字段 + extract）
    method: POST
    path: /api/auth/login
    body: { username: "${variables.ACCOUNT}", password: "${variables.PASSWORD}" }
    extract:
      - { name: token, type: jsonpath, expr: "$.data.access_token" }
  injectHeader:
    Authorization: "Bearer ${token}"   # 按模板生成注入头
  applyTo: all                          # all | [用例名...] | none（默认 all）
  retryOn401: true                      # 401 时重新登录并重试一次
```

### asserts（断言）

| type | 配置 | 说明 |
|------|------|------|
| `status` | `expected: 200` | HTTP 状态码 |
| `jsonpath` | `expr` + `equals/contains/exists/notEmpty` 之一 | JSONPath 断言（期望值支持插值） |
| `rtMs` | `max: 3000` | 响应时间上限（毫秒） |
| `bodyContains` | `text: "success"` | 响应体包含文本（支持插值） |
| `header` | `name` + `equals/contains` | 响应头断言（期望值支持插值） |

### 数据驱动（dataSets）

```yaml
dataSets:
  - test: create-item        # 引用 tests 中已定义的用例名
    file: data/items.csv     # 相对配置文件所在目录
    bind:
      - { name: itemName, column: itemName }
      - { name: itemQty, column: itemQty }
    mode: each               # 每行数据跑一次该用例
```

CSV 首行为表头，支持引号包裹（`"banana,large"`）与 `""` 转义。

## 项目结构

```
src/main/java/com/testknow/webtest/
├── Main.java            CLI 入口
├── cli/                 run 命令 + 退出码契约
├── config/              YAML 模型 / 加载 / 校验（含环境/鉴权/数据驱动）
├── core/
│   ├── Variables.java           不可变作用域变量链
│   ├── PlaceholderResolver.java 占位符插值引擎
│   ├── extract/                 变量提取器 SPI + jsonpath/regex/header/body
│   ├── auth/                    AuthManager（登录/静态鉴权/401 重登）
│   ├── dataset/                 CSV 读取 + 数据驱动执行
│   ├── CaseExecutor / TestRunner
│   └── result/                  结果模型
├── http/                OkHttp 封装（超时/重试/日志/鉴权头注入）
├── assertion/           断言 SPI + 5 内置断言
└── report/              result.json 输出 + 控制台汇总
```

## 开发

```bash
mvn test          # 51 个自测（WireMock 本地 mock 后端）
```

## 路线图（后续阶段）

- **Phase C**：断言/提取插件 SPI（ServiceLoader）、HTML 报告（带图表）
- **Phase D**：性能引擎（并发 / TPS / P50/P95/P99 / 阈值门禁）
- **Phase E**：稳定性压测 + 安全检查
- **Phase F**：CI 集成、离线报告回放、多站点配置

## 环境要求

- JDK 21、Maven 3.9+（3.6.3 亦可用）
- 若 `JAVA_HOME` 指向旧 JDK，构建前先 `export JAVA_HOME="<JDK21路径>"`
