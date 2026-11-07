# Agent Gateway

一个用 Go 标准库 + `segmentio/kafka-go` 实现的 **API 网关探针**：把收到的请求转发到目的地址，同时采集中转的**完整接口调用（请求 + 响应）**，生成**结构化日志**并投递到 **Kafka**，用于后续分析。

- **透明转发**：基于 `net/http/httputil.ReverseProxy`，支持 HTTP/1.1、HTTP/2、流式响应（SSE）、WebSocket 升级。
- **完整采集**：一次接口调用合并为**一条交易记录**（request + response），默认**完整记录 body、不截断**。
- **标准化输出**：JSON 格式，带 `schema_version`，字段稳定，便于入库/分析。
- **异步投递 Kafka**：内存批量缓冲，**不阻塞被代理的接口**。
- **失败兜底**：Kafka 不可达或队列满时落**本地 spool 文件**（JSON Lines），恢复后自动**回放补发**，尽量不丢日志。
- **可观测**：内置 `/__agent-gateway/stats` 统计端点。

## 数据流

```
client ──► [agent-gateway :8080] ──► upstream (target)
                   │
                   ├─ 采集 request + response
                   ├─ 组装 Transaction (JSON)
                   ├─ 批量异步 produce ──► Kafka (topic)
                   └─ 失败/队列满 ──► 本地 spool 文件 ──(定期)──► 重投 Kafka
```

## 快速开始

```powershell
# 依赖
go mod download

# 运行（转发到目标，记录并投递 Kafka）
go run . --target http://10.10.5.71:8080 --kafka-brokers 127.0.0.1:9092 --kafka-topic agent-gateway

# 或用配置文件
go run . --config config.example.json
```

启动后会打印监听地址与输出配置。请求示例：

```powershell
curl -i -X POST "http://localhost:8080/gateway/users?page=2" -H "Authorization: Bearer x" -H "Content-Type: application/json" -d '{"name":"alice"}'
```

调试时可加 `--console` 把记录同时打印到标准输出。

## 交易记录格式

每次接口调用产生**一条** Kafka 消息，value 为该 JSON（已去除 HTML 转义、单行）：

```json
{
  "schema_version": "1.0",
  "type": "agent-gateway.transaction",
  "time": "2026-10-08T17:40:48.6340884+08:00",
  "id": "b8edfc5301940a97",
  "duration_ms": 2.229,
  "upstream": "http://127.0.0.1:19000",
  "gateway": { "listen": ":18090", "target": "http://127.0.0.1:19000" },
  "request": {
    "method": "POST", "scheme": "http", "host": "127.0.0.1:18090",
    "path": "/gateway/users", "query": "page=2", "uri": "/gateway/users?page=2",
    "proto": "HTTP/1.1", "remote_addr": "127.0.0.1:17319", "client_ip": "127.0.0.1",
    "user_agent": "curl/8.0",
    "headers": { "Authorization": ["[REDACTED]"], "Content-Type": ["application/json"] },
    "content_type": "application/json", "content_length": 16,
    "body": "{\"name\":\"alice\"}", "body_size": 16
  },
  "response": {
    "status": 201, "status_text": "Created",
    "headers": { "Content-Type": ["application/json"] },
    "content_type": "application/json", "content_length": 73,
    "body": "{\"upstream_path\":\"/gateway/users\"}", "body_size": 73,
    "bytes_out": 73, "upstream_ms": 1.721
  }
}
```

要点：

| 字段 | 说明 |
| --- | --- |
| `id` | 每次调用唯一；默认作为 Kafka 消息 key，保证同一调用落到同一分区 |
| `duration_ms` | 端到端耗时（收到请求 → 响应写回完成） |
| `response.upstream_ms` | 上游耗时（收到上游响应头，即 TTFB） |
| `body_size` / `bytes_out` | 记录的 body 字节数 / 实际写回客户端的字节数（101 升级时为上游→客户端的隧道字节数） |
| `bytes_in` | 仅 101 升级连接：客户端→上游的隧道字节数 |
| `upgrade` | 仅 101 升级连接：协商的协议名，如 `websocket` |
| `body_truncated` | 仅在设置了 `max_body_size` 且超限时为 `true`（默认不截断，字段省略） |
| `error` | 上游错误（此时状态码 502），仅出错时出现 |
| `gateway` | 产生该记录的网关实例（监听地址 + 目标） |

Kafka 消息 **key** 由 `kafka.key` 决定：`id`（默认）、`client_ip`、`none`（无 key，改用 `LeastBytes` 负载均衡）。

## 配置

参考 `config.example.json`。完整字段：

| 字段 | 说明 | 默认 |
| --- | --- | --- |
| `listen` | 监听地址 | `:8080` |
| `target` | 目的地址（上游 base URL） | 必填 |
| `timeout` | 等待上游响应头超时 | `30s` |
| `strip_prefix` | 转发前去除的路径前缀 | `[]` |
| `add_prefix` | 目标 base path 与请求路径之间插入的路径 | `""` |
| `add_headers` | 转发时设置/覆盖的请求头 | `{}` |
| `remove_headers` | 转发时删除的请求头 | `[]` |
| `insecure_skip_verify` | 跳过上游证书校验 | `false` |
| `tls.cert` / `tls.key` | 配置后监听侧启用 HTTPS | `""` |
| `capture.headers` | 是否记录请求/响应头 | `true` |
| `capture.body` | 是否记录 body | `true` |
| `capture.max_body_size` | 单条 body 最多记录字节数；`0` 不限（完整） | `0` |
| `capture.redact_headers` | 日志脱敏头（大小写不敏感）；`[]` 关闭 | Authorization/Cookie 等 |
| `output.console` | 记录同时打印到 stdout | `false` |
| `output.file` | 记录本地存档文件 | `""` |
| `output.kafka.*` | Kafka 生产者配置，见下 | 见下 |

### Kafka（`output.kafka`）

| 字段 | 说明 | 默认 |
| --- | --- | --- |
| `enabled` | 启用 Kafka 输出 | `true` |
| `brokers` | broker 列表 | `["127.0.0.1:9092"]` |
| `topic` | 目标 topic | `agent-gateway` |
| `client_id` | 客户端标识 | `agent-gateway` |
| `key` | 消息 key：`id` / `client_ip` / `none` | `id` |
| `batch_size` | 单批最大条数 | `500` |
| `batch_timeout` | 凑批超时 | `1s` |
| `queue_size` | 内存队列上限（满则落盘） | `10000` |
| `write_timeout` | 单次 produce 超时 | `5s` |
| `max_attempts` | 内部重试次数 | `3` |
| `required_acks` | `all` / `local` / `none` | `all` |
| `compression` | `none` / `gzip` / `snappy` / `lz4` / `zstd` | `none` |
| `tls.*` | TLS：`enabled`、`ca_file`、`cert_file`、`key_file`、`insecure_skip_verify` | 关闭 |
| `sasl.*` | SASL：`enabled`、`mechanism`(`plain`/`scram-sha-256`/`scram-sha-512`)、`username`、`password` | 关闭 |

### 失败兜底（`output.kafka.fallback`）

| 字段 | 说明 | 默认 |
| --- | --- | --- |
| `enabled` | 启用本地 spool | `true` |
| `file` | spool 文件路径（JSON Lines，`{"key":..,"value":..}`） | `agent-gateway-spool.ndjson` |
| `replay` | 定期回放补发 | `true` |
| `replay_interval` | 回放周期 | `30s` |

## 命令行参数

| 参数 | 说明 |
| --- | --- |
| `--config` | JSON 配置文件路径 |
| `--listen` | 监听地址 |
| `--target` | 目的地址 |
| `--timeout` | 上游响应头超时 |
| `--console` | 记录同时打印到 stdout |
| `--log-file` | 记录本地存档文件 |
| `--capture-body` | 是否记录 body（`true`/`false`） |
| `--max-body` | 单条 body 最多记录字节数；`0` 不限 |
| `--kafka-enabled` | 启用 Kafka 输出（`true`/`false`） |
| `--kafka-brokers` | 逗号分隔 broker 列表 |
| `--kafka-topic` | 目标 topic |

命令行参数会覆盖配置文件中的同名字段。

## 内置端点

- `GET /__agent-gateway/health` → `{"status":"ok"}`
- `GET /__agent-gateway/stats` → 采集/发送/落盘/回放计数与 Kafka 配置摘要

两者都不会被转发到上游。

## 可靠性说明

- **投递语义：至少一次（at-least-once）**。produce 失败时会整批回退到 spool，若部分消息实际已写入可能产生重复，下游需按 `id` 幂等去重。
- **不阻塞转发**：`Write` 仅入队；队列满时直接落盘，绝不在请求路径上等待 Kafka。
- **spool 回放**：回放时先原子轮转当前 spool 文件再重投，成功即删除；失败则把记录重新追加回 spool，不丢数据。
- **内存**：默认完整采集 body，大文件/长连接流式响应会使内存增长，必要时用 `capture.max_body_size` 限制（仅影响日志，转发始终完整）。

## 运行测试

```powershell
go test ./...
```

覆盖：转发与路径重写、交易记录内容、脱敏、body 完整/截断、body/headers 关闭、上游 502、Kafka 批量发送、发送失败落盘、队列满落盘、spool 回放与重投失败、消息 key 选择。

## Docker

多阶段构建：`golang:1.22-alpine` 编译出静态二进制，运行在 `alpine:3.20`，以非 root 用户运行，仅保留 CA 证书与时区数据。

```powershell
# 构建镜像
docker build -t agent-gateway:latest .

# 运行（把 <目标地址> 和 <kafka 地址> 换成实际值）
docker run --rm -p 8080:8080 `
  -v "${PWD}/data:/data" `
  agent-gateway:latest `
  --target=http://10.10.5.71:8080 `
  --kafka-brokers=kafka.example.com:9092 `
  --kafka-topic=agent-gateway
```

要点：

- **`ENTRYPOINT` 是探针本身**，`docker run` 镜像名之后的参数会直接传给探针（如上）。
- **`/data` 是可写数据卷**，默认的兜底 spool 文件（`agent-gateway-spool.ndjson`）会写在这里；建议挂载出来，避免容器重建后丢失。用 `--config` 指定配置时，相对路径也基于 `/data`。
- **默认监听 `:8080`**，已 `EXPOSE`。若用 `--listen` 改端口，请同时设置环境变量 `LISTEN_PORT`，否则健康检查会失败，例如：
  ```
  docker run -e LISTEN_PORT=9090 -p 9090:9090 agent-gateway:latest --listen=:9090 --target=...
  ```
- **健康检查**：内置 `HEALTHCHECK` 定期请求 `/__agent-gateway/health`。
- **容器内默认 Kafka 地址是 `127.0.0.1:9092`**（指向容器自身），务必用 `--kafka-brokers` 覆盖为实际 broker 地址（如 `kafka:9092`）。
- 可用 `--config` 挂载配置文件：`-v "${PWD}/config.example.json:/data/config.json" ... --config=/data/config.json`。

### 本地联调（probe + Kafka）

`docker-compose.yml` 起一个单节点 Kafka 和探针，方便验证整条链路：

```powershell
docker compose up --build
# 另开终端查看写入的 topic
docker compose exec kafka kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic agent-gateway --from-beginning
```
