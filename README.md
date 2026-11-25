# ModVault

大模型网关（MVP）：**Anthropic → OpenAI 单向协议转换网关**。

- 对客户端：表现为 Anthropic Messages API（`POST /v1/messages`、`POST /v1/messages/count_tokens`）。
- 对上游：表现为 OpenAI Chat Completions API。
- 目标：Claude Code / Anthropic SDK 只改 `ANTHROPIC_BASE_URL` 与 `ANTHROPIC_AUTH_TOKEN`（网关 Key）即可用；上游厂商密钥只存在于网关，业务方零接触。

## 技术栈

Java 8 + Spring Boot 2.7.18（Spring MVC 阻塞式 SSE）+ MyBatis + PostgreSQL + Flyway + OkHttp + Micrometer/Prometheus。

## 快速开始

### 1. 生成主密钥（AES-256-GCM，32 字节）

```bash
openssl rand -base64 32
```

### 2. 准备环境变量

```bash
cp .env.example .env
# 编辑 .env，填入 MODVAULT_MASTER_KEY 与 MODVAULT_ADMIN_TOKEN（>= 32 字符）
```

### 3. 一键启动

```bash
docker compose up -d
docker compose ps          # 等待 app 健康
curl -s localhost:8080/actuator/health
```

### 4. 配置一条转换链路（管理 API，Bearer 用 MODVAULT_ADMIN_TOKEN）

```bash
ADMIN="Authorization: Bearer $MODVAULT_ADMIN_TOKEN"
BASE=localhost:8080

# 建上游（OpenAI 兼容）
curl -s -X POST "$BASE/admin/api/v1/upstreams" -H "$ADMIN" -H 'Content-Type: application/json' \
  -d '{"name":"openai","base_url":"https://api.openai.com/v1"}'

# 存上游密钥（返回掩码）
curl -s -X PUT "$BASE/admin/api/v1/upstreams/<UPSTREAM_ID>/key" -H "$ADMIN" -H 'Content-Type: application/json' \
  -d '{"key":"sk-..."}'

# 建模型映射：claude 模型名 -> 上游模型名
curl -s -X POST "$BASE/admin/api/v1/model-mappings" -H "$ADMIN" -H 'Content-Type: application/json' \
  -d '{"anthropic_model":"claude-sonnet-4-20250514","upstream_id":"<UPSTREAM_ID>","upstream_model":"gpt-4o"}'

# 签发网关 Key（明文仅返回这一次）
curl -s -X POST "$BASE/admin/api/v1/api-keys" -H "$ADMIN" -H 'Content-Type: application/json' \
  -d '{"name":"team-a"}'
```

### 5. 用 Claude Code 接入

```bash
export ANTHROPIC_BASE_URL=http://localhost:8080
export ANTHROPIC_AUTH_TOKEN=<上一步返回的 sk-mv-...>
claude
```

## 配置项

| 环境变量 | 说明 | 默认 |
| --- | --- | --- |
| `MODVAULT_PORT` | 监听端口 | `8080` |
| `MODVAULT_DB_URL` | JDBC 连接串 | `jdbc:postgresql://localhost:5432/modvault` |
| `MODVAULT_DB_USER` / `MODVAULT_DB_PASSWORD` | 数据库账号 | `modvault` / `modvault` |
| `MODVAULT_MASTER_KEY` | base64 编码的 32 字节 AES 主密钥（必填） | — |
| `MODVAULT_ADMIN_TOKEN` | 管理面静态 Bearer Token（>= 32 字符，必填） | — |
| `MODVAULT_AUTO_APPEND_V1` | 裸 base_url 自动补 `/v1` | `true` |
| `MODVAULT_DEFAULT_CONNECT_TIMEOUT_MS` | 上游连接超时 | `10000` |
| `MODVAULT_DEFAULT_READ_TIMEOUT_MS` | 上游读超时 | `120000` |
| `MODVAULT_MAX_REQUEST_BODY_BYTES` | 请求体上限 | `2097152`（2MB） |
| `MODVAULT_RETENTION_DAYS` | 用量记录保留天数 | `90` |

## API

数据面：`POST /v1/messages`（非流式）、`POST /v1/messages/count_tokens`、`GET /actuator/health`、`GET /actuator/health/readiness`、`GET /actuator/prometheus`。

管理面（Bearer `MODVAULT_ADMIN_TOKEN`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/admin/api/v1/upstreams` | 上游列表/创建 |
| GET/PATCH/DELETE | `/admin/api/v1/upstreams/{id}` | 详情/更新/删除 |
| PUT | `/admin/api/v1/upstreams/{id}/key` | 设置/替换上游密钥（幂等，单密钥） |
| GET/POST | `/admin/api/v1/model-mappings` | 映射列表/创建 |
| PATCH/DELETE | `/admin/api/v1/model-mappings/{id}` | 更新/删除 |
| GET/POST | `/admin/api/v1/api-keys` | 网关 Key 列表/创建（创建返回唯一明文） |
| DELETE | `/admin/api/v1/api-keys/{id}` | 吊销 |
| GET | `/admin/api/v1/usage` | 用量列表（`limit`/`offset`） |

OpenAPI 文档：`/v3/api-docs` 与 `/swagger-ui/index.html`。

## 错误语义（Anthropic）

所有错误返回 `{"type":"error","error":{"type":T,"message":M}}`：

| 场景 | HTTP | `error.type` |
| --- | --- | --- |
| 请求体非法 / 缺必填 | 400 | `invalid_request_error` |
| 网关 Key 缺失/无效/吊销 | 401 | `authentication_error` |
| 模型未映射/映射停用 | 404 | `not_found_error` |
| 请求体超过 2MB | 413 | `request_too_large` |
| 上游 400/422 | 400 | `invalid_request_error` |
| 上游 401/403（平台密钥问题） | 500 | `api_error` |
| 上游 404 | 404 | `not_found_error` |
| 上游 429 | 429 | `rate_limit_error` |
| 上游 5xx / 连接失败 / 超时 | 529 | `overloaded_error` |

## 当前里程碑状态

- **M1 骨架与直通（已实现）**：工程骨架、Flyway 迁移、上游/上游密钥（AES-256-GCM + 掩码）、模型映射、网关 Key、`POST /v1/messages` 非流式文本 + 工具调用转换、`count_tokens` 本地估算、健康检查、Prometheus 指标、JSON 结构化日志、用量记录。
- **M2 流式与工具（已实现核心）**：`stream=true` 的 SSE 事件转换（`message_start` / `content_block_start` / `content_block_delta` / `content_block_stop` / `message_delta` / `message_stop`）、工具调用增量（`input_json_delta`）、流中错误归一（`event: error`）、客户端断开释放。有限重试（FR-19）与优雅停机（FR-28）尚未实现。
- **M3 收口（部分）**：Dockerfile / docker compose / README / OpenAPI 已就绪；NFR-01/02/03 压测报告未做。

## 密钥安全

- 上游密钥 AES-256-GCM 加密落库（密文 `v1:` 前缀，AAD 绑定 upstream_id+key_id）；查询只返回掩码。
- 网关 Key 只存 SHA-256 哈希 + 8 位前缀，明文仅创建时返回一次。
- 库 / 日志 / 响应中无密钥明文。

## 本地构建与测试

```bash
mvn test          # 单元测试 + Spring 上下文冒烟测试
mvn package       # 打包可运行 jar
```
