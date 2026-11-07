# Gitee Issue 报告

- 生成时间：2026-11-07T20:59:24+08:00
- 范围：`gitee` 全部 open issue，已过滤 Pull Request。
- 数量：15

## 处理结果

- `gitee#IKTO41`–`gitee#IKTO4F`：按用户要求不处理，已全部通过 Gitee 网页端关闭。

## gitee#IKTO4F — [P4] agent-gateway: Kafka 默认值与回放事件时间不合理
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO4F
- 作者：qihoho
- 更新时间：2026-11-07T20:46:51+08:00
### 需求摘要
**现象**
- `compression` 默认 `none`，大 body 场景网络/存储成本高；
- `AllowAutoTopicCreation: true`，topic 拼错会静默创建新 topic；
- `BatchBytes` 不可配置，与默认不截断 body 冲突；
- 回放时 `kafka.Message.Time` 被重置为 `time.Now()`，丢失原始事件时间；
- `MaxAttempts` 与 spool 重试叠加，可能放大重复。
**原因**
相关代码：`synap-agent/agent-gateway/kafka.go:212`、`kafka.go:73`、`spool.go:208`。
**影响**
成本高、topic 误创建、事件时间语义错误、重复放大。
**修复建议**
- 默认 `compression=zstd`（或 lz4）；
- `AllowAutoTopicCreation` 默认 false，可配置开启；
- 暴露 `BatchBytes` 并在启动时校验；
- 回放保留原始 `time`（从记录 JSON 或 spool envelope 读取）；
- 明确 at-least-once 语义与去重键。
**验收标准**
- 默认压缩生效；
- topic 不存在时明确报错而不是自动创建；
- 回放的 Kafka reco…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## gitee#IKTO4E — [P4] agent-gateway: 记录 schema 缺少完整性/编码/追踪字段，ID 仅 64-bit
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO4E
- 作者：qihoho
- 更新时间：2026-11-07T20:46:48+08:00
### 需求摘要
**现象**
记录 schema 缺少若干分析必需字段：
- `body_encoding`/`content_encoding`（无法还原压缩/二进制 body）；
- `body_complete`/`aborted`（无法区分完整与截断）；
- `ttfb_ms` 与 DNS/连接/TLS 分阶段耗时；
- `trace_id`/`span_id`（无法与上游/APM 关联）；
- `instance_id`/`version`；
- UTC 时间与 epoch millis。
另外 `id` 只有 8 字节随机数（64-bit），高并发下存在碰撞风险；`rand` 失败时回退为固定 `req-unknown`。
**原因**
相关代码：`record.go`、`proxy.go` 的 `newID`。
**影响**
下游难以做精确分析、跨系统关联和多副本归因；极端情况下 ID 冲突会破坏幂等/去重。
**修复建议**
- 扩展 schema（建议升到 1.1）并保持向后兼容；
- 使用 UUIDv7/ULID 或至少 16 字节随机 ID，失败时使用时间+计数器兜底；
- 增加 `gateway.version`/`instance`；
- 时间统一 UTC，并输出 epoch millis。
**验收标准**
- 二进制/压缩 body 可还原；
- 记录包含完整性与耗…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少复现步骤或运行环境

## gitee#IKTO4D — [P4] agent-gateway: 缺少可信代理支持，client_ip/scheme 在 LB 后失真
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO4D
- 作者：qihoho
- 更新时间：2026-11-07T20:46:45+08:00
### 需求摘要
**现象**
- `client_ip` 直接取 `RemoteAddr`，网关在 LB/Ingress 后面时记录的是 LB IP，`kafka.key=client_ip` 会把所有流量打到同一分区；
- `request.scheme` 只看本地 `r.TLS`，TLS 在 LB 终止时永远记录为 http；
- `out.Host` 固定为 target host，上游若依赖原始 Host 无法工作。
**原因**
`clientIP` 只解析 `RemoteAddr`；`requestScheme` 只检查 `r.TLS`；`rewrite` 设置 `out.Host = g.target.Host`。
相关代码：`synap-agent/agent-gateway/proxy.go:495`、`proxy.go:507`、`proxy.go:211`。
**影响**
客户端 IP 维度分析失真、分区倾斜、日志 scheme 错误、部分虚拟主机型上游无法使用。
**修复建议**
- 增加 `trusted_proxies` 配置，按可信来源解析 X-Forwarded-For/Forwarded；
- 支持 X-Forwarded-Proto 覆盖 `scheme`；
- 增加 `preserve_host` 选项；
- 记录中同时保留 peer IP 与解析后的 cl…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## gitee#IKTO4C — [P4] agent-gateway: 缺少采样、路径过滤、仅错误捕获与 header 白名单
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO4C
- 作者：qihoho
- 更新时间：2026-11-07T20:46:42+08:00
### 需求摘要
**现象**
网关对所有请求 100% 捕获完整 body，没有采样、路径/方法过滤、仅错误捕获或 header 白名单。高 QPS 或静态资源/健康检查流量会放大 Kafka、磁盘和 CPU 成本。
**原因**
`ServeHTTP` 对每个非内部请求都创建 exchange 并 emit；`CaptureConfig` 只有全局 headers/body/max_body_size。
相关代码：`synap-agent/agent-gateway/proxy.go:269`、`config.go:53`。
**影响**
- 日志量随 QPS 线性增长，成本不可控；
- 大量低价值请求淹没关键错误；
- 大 body 采样进一步放大内存和 Kafka 毒丸风险。
**修复建议**
- 增加 `capture.sample_rate`（按请求或按路径）；
- 增加 include/exclude paths、methods；
- 支持 `only_on_error`（4xx/5xx 或上游错误才捕获 body）；
- 增加 header allowlist，减少记录体积；
- 采样决策写入记录，便于下游加权分析。
**验收标准**
- 可按比例/路径/状态码控制捕获；
- 关键错误仍 100% 捕获；
- 采样配置可观测。
代码审查确认。
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少复现步骤或运行环境；缺少预期结果

## gitee#IKTO4B — [P3] agent-gateway: 关键场景测试缺失，代码全部集中在 package main
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO4B
- 作者：qihoho
- 更新时间：2026-11-07T20:46:40+08:00
### 需求摘要
**现象**
当前覆盖率约 57.7%，缺少以下关键场景：
- WebSocket/101 升级；
- SSE/长连接；
- 客户端中断/上游中断；
- gzip/br 与二进制 body；
- 编码路径与 strip_prefix 边界；
- 超大 spool 行、损坏行、崩溃恢复；
- 负 duration/未知字段等配置边界；
- 并发关闭与队列满。
代码全部在 `package main`，proxy/sink/spool/config 之间没有包边界，测试只能通过整包构造。
**原因**
相关文件：`synap-agent/agent-gateway/*_test.go`，当前只有少量正向用例。
**影响**
上述 P0/P1 缺陷都能绕过现有测试；后续修改容易回归；模块难以复用和独立测试。
**修复建议**
- 补齐上述场景的回归测试，并加入 `-race`；
- 增加 `go test -bench` 或压测脚本，覆盖大 body/高并发；
- 按 `internal/proxy`、`internal/sink`、`internal/spool`、`internal/config` 拆分；
- 为 path join、redaction、config duration 增加 fuzz 测试。
**验收标准**
- 关键路径覆盖率显著提升；
- P0/P1 每个缺陷都…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：高
- 待补充信息：缺少预期结果

## gitee#IKTO4A — [P3] agent-gateway: Docker/CI/依赖与基础镜像过期，TLS 下 healthcheck 失效
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO4A
- 作者：qihoho
- 更新时间：2026-11-07T20:46:37+08:00
### 需求摘要
**现象**
- `go.mod`/Dockerfile 使用 Go 1.22，`alpine:3.20` 均已过期；
- 启用 TLS 后 Docker healthcheck 仍请求 `http://127.0.0.1:8080/...`，容器会一直 unhealthy；
- compose 使用 `bitnami/kafka:3.7`（Bitnami 公共镜像目录已迁移，可能无法拉取），并硬编码 `10.10.5.71`；
- `./data:/data` bind mount 在 Linux 上通常为 root 所有，非 root 容器用户可能无法写 spool；
- 仓库没有针对该模块的 build/test/vet/lint/govulncheck CI。
**原因**
相关文件：`synap-agent/agent-gateway/go.mod`、`Dockerfile`、`docker-compose.yml`、`.github/workflows/`。
**影响**
安全补丁缺失、部署后健康检查误报、Kafka 镜像不可用、spool 启动失败、回归问题无法在 CI 拦截。
**修复建议**
- 升级 Go 与基础镜像到受支持版本；
- healthcheck 根据 TLS 配置选择 http/https，或改查独立 admin 端口；
- 固定镜像 diges…
### 修复范围
修复模板、探针或执行器逻辑，运行 Go 测试与前端脚本检查，同步 OpenAPI 和 README。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Synap Build
- 优先级：中
- 待补充信息：缺少预期结果

## gitee#IKTO49 — [P3] agent-gateway: 优雅关闭存在竞态，可能丢记录或写入已关闭资源
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO49
- 作者：qihoho
- 更新时间：2026-11-07T20:46:34+08:00
### 需求摘要
**现象**
- serve goroutine 中 `log.Fatalf` 会直接 `os.Exit`，跳过 spool/sink 清理；
- `srv.Shutdown` 10 秒超时后仍会关闭 sink/spool，而 SSE/长连接 handler 可能还在写；
- `Spool.Write` 在 `Close` 之后会因 `s.f == nil` 自动重新打开文件；
- `KafkaSink.Write` 与 `Close` 之间存在“已通过 closed 检查、但入队后 worker 已退出”的窗口，消息可能静默丢失。
**原因**
相关代码：`synap-agent/agent-gateway/main.go:151`、`main.go:162`、`spool.go:50`、`kafka.go:72`。
**影响**
滚动发布/重启时可能丢记录、泄漏 fd，或在关闭过程中继续写入已关闭的资源。
**修复建议**
- serve 错误通过 channel 传回主 goroutine，统一走优雅关闭；
- 用 WaitGroup 等待所有 handler 结束，或在超时后显式 `srv.Close()` 并标记丢弃；
- `Spool` 增加 closed 状态，Close 后拒绝写入；
- `KafkaSink` 用 RWMutex/关闭信号保证 Write 与 C…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## gitee#IKTO48 — [P3] agent-gateway: 指标与健康检查不足，多副本无法归因
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO48
- 作者：qihoho
- 更新时间：2026-11-07T20:46:31+08:00
### 需求摘要
**现象**
- 只有累计计数器，没有请求延迟直方图、队列深度、spool 字节数、状态码分布；
- `/health` 永远返回 ok，即使 Kafka 长时间不可用、spool 持续增长；
- 记录里的实例标识只有 `listen`，多副本都是 `:8080` 无法区分；
- 没有版本/构建信息，无法判断线上行为对应哪个版本。
**原因**
`Metrics` 只有计数器；`serveInternal` 的 health 固定返回 `{"status":"ok"}`；`GatewayInfo` 只有 listen/target。
相关代码：`synap-agent/agent-gateway/metrics.go`、`proxy.go:338`。
**影响**
无法做容量规划、故障定位和灰度对比；K8s 不会在“日志链路已失效”时重启/摘流；多副本数据无法归因。
**修复建议**
- 增加 Prometheus `/metrics`（延迟直方图、状态码、队列深度、spool 字节/条数、丢弃原因）；
- 增加 `/ready`，反映 Kafka 可用性、spool backlog、最近成功发送时间；
- 记录中增加 `instance_id`/hostname/pod/version；
- 暴露 build info（git sha、构建时间）。
**验收标准**
- 可采集…
### 修复范围
修复模板、探针或执行器逻辑，运行 Go 测试与前端脚本检查，同步 OpenAPI 和 README。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Synap Build
- 优先级：中
- 待补充信息：缺少预期结果

## gitee#IKTO47 — [P3] agent-gateway: 配置校验缺口会导致启动 panic、忙循环或静默关闭限制
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO47
- 作者：qihoho
- 更新时间：2026-11-07T20:46:28+08:00
### 需求摘要
**现象**
- `replay_interval` 为负数时 `time.NewTicker` 会 panic；
- `batch_timeout` 为负数时 `time.NewTimer` 立即触发，形成忙循环；
- `batch_timeout`/`write_timeout`/`replay_interval` 字符串非法时会静默回退默认值，不报错；
- 未知 JSON 字段被忽略，字段拼写错误无感知；
- `max_body_size` 负数会被静默改成 0（不限制）。
**原因**
`ApplyDefaults` 对多个 duration 忽略 parse error；`Validate` 只校验 `timeout`；`LoadConfig` 使用普通 `json.Unmarshal`。
相关代码：`synap-agent/agent-gateway/config.go:292`。
**影响**
配置错误可能在启动时崩溃、运行时 CPU 100% 或静默关闭关键限制，排查成本高。
**修复建议**
- 使用 `json.Decoder.DisallowUnknownFields`；
- 统一校验所有 duration > 0，非法值直接报错；
- 对 `max_body_size`、`queue_size`、`batch_size` 等设置上下界；
- 增加 `--c…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：高
- 待补充信息：缺少预期结果

## gitee#IKTO46 — [P2] agent-gateway: TLS 缺少最低版本/mTLS，cert 或 key 半配置时静默降级为 HTTP
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO46
- 作者：qihoho
- 更新时间：2026-11-07T20:46:10+08:00
### 需求摘要
**现象**
1）监听侧 TLS 未设置 `MinVersion`，也未支持 mTLS；
2）只配置 `tls.cert` 或只配置 `tls.key` 时，启动逻辑会静默回退为 HTTP，不会报错；
3）Kafka TLS 的 `InsecureSkipVerify` 可被随意开启且没有告警。
**原因**
`main` 只有在 cert/key 同时非空时才启用 HTTPS；`tls.Config` 未设置最低版本/客户端认证；`Validate` 不校验 cert/key 成对。
相关代码：`synap-agent/agent-gateway/main.go:133`、`config.go:292`、`kafka.go` 的 `buildTLS`。
**影响**
半配置导致明文传输而不自知；缺少最低 TLS 版本和客户端认证，不满足常见安全基线。
**修复建议**
- 启动时强制校验 cert/key 必须成对出现；
- 设置 `MinVersion: tls.VersionTLS12`（或更高）并支持 cipher 配置；
- 支持客户端 CA/mTLS；
- 对 `insecure_skip_verify` 输出显式警告，生产环境建议禁用。
**验收标准**
- 半配置启动失败并给出明确错误；
- 默认最低 TLS 1.2；
- 可配置 mTLS；
- 新增 TLS 配…
### 修复范围
修复模板、探针或执行器逻辑，运行 Go 测试与前端脚本检查，同步 OpenAPI 和 README。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Synap Build
- 优先级：中
- 待补充信息：缺少预期结果

## gitee#IKTO45 — [P2] agent-gateway: target userinfo 与 Kafka SASL 密码等 secret 处理不安全
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO45
- 作者：qihoho
- 更新时间：2026-11-07T20:46:07+08:00
### 需求摘要
**现象**
1）`target` 若包含 `user:pass@host`，会原样写入记录的 `upstream` 与 `gateway.target`；
2）Kafka SASL 密码只能明文写在 JSON 配置中；
3）没有环境变量/secret 文件引用机制，容器部署时凭证容易进入镜像或命令历史。
**原因**
`GatewayInfo`/`Transaction.Upstream` 使用 `g.target.String()`；`KafkaSASLConfig.Password` 直接来自配置；`LoadConfig` 不支持 `${ENV}` 或 `*_file`。
相关代码：`synap-agent/agent-gateway/proxy.go:275`、`config.go`。
**影响**
凭证可能进入 Kafka、日志和 spool，形成长期泄露；配置仓库/镜像中可能出现明文密码。
**修复建议**
- 记录前剥离 URL userinfo，只保留 scheme/host/path；
- 支持 `password_file` 或环境变量替换，禁止日志打印配置中的 secret；
- 对配置中的 secret 字段做统一封装，避免 `%+v` 泄露；
- 文档增加密钥管理建议。
**验收标准**
- 记录中不出现 URL userinfo/SASL 密码；
-…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## gitee#IKTO44 — [P2] agent-gateway: 缺少入站认证、限流、请求体上限与 body 读取超时
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO44
- 作者：qihoho
- 更新时间：2026-11-07T20:46:04+08:00
### 需求摘要
**现象**
网关没有入站认证、限流、请求体大小限制和 body 读取超时。只要能访问监听端口，就可以借网关访问固定上游；慢速上传可长期占用连接和内存。
**原因**
`http.Server` 只设置了 `ReadHeaderTimeout` 与 `IdleTimeout`，没有 `ReadTimeout`/per-request deadline；`captureRequestBody` 没有 `MaxBytesReader`。
相关代码：`synap-agent/agent-gateway/main.go:126`、`proxy.go:401`。
**影响**
- 网关成为未授权的上游访问入口；
- 超大 body/慢速 body 可造成内存或连接耗尽；
- 无法限制单客户端或全局 QPS。
**修复建议**
- 支持 mTLS、Bearer token 或来源 IP allowlist；
- 增加请求体上限与 body 读取 deadline；
- 增加全局/按 IP 并发与速率限制；
- 将限制配置化并暴露拒绝计数指标。
**验收标准**
- 未授权请求被拒绝；
- 超大/慢速 body 不会耗尽资源；
- 限流行为可配置、可观测。
代码审查确认。
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少复现步骤或运行环境；缺少预期结果

## gitee#IKTO43 — [P2] agent-gateway: 默认全量捕获 body，但只脱敏 header，敏感数据可进入 Kafka/磁盘
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO43
- 作者：qihoho
- 更新时间：2026-11-07T20:46:01+08:00
### 需求摘要
**现象**
默认 `capture.body=true` 且 `max_body_size=0`，会完整记录请求/响应 body；脱敏只覆盖 header，query 中的 `access_token`、body 中的密码/身份证/手机号等会原样进入 Kafka。spool 与 `--log-file` 使用 `0644`，同机其他用户可读。
**原因**
`CaptureConfig.RedactHeaders` 只作用于 header；`record.RequestRec.Query/Body` 没有脱敏；`OpenSpool`/`newFileSink` 使用 `0644`。
相关代码：`synap-agent/agent-gateway/config.go:54`、`record.go:53`、`spool.go:41`、`sink.go:51`。
**影响**
合规风险（PII/凭证落盘与入 Kafka）；多租户或共享主机上日志可被其他用户读取；默认无上限会放大泄露范围。
**修复建议**
- 默认设置合理的 `max_body_size`（如 256KB/1MB），完整捕获需显式开启；
- 增加 query 参数脱敏（token/password/signature 等）和 JSON 字段脱敏规则；
- spool/log 文件权限改为 `0600`；
- 增加保留…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少复现步骤或运行环境；缺少预期结果

## gitee#IKTO42 — [P2] agent-gateway: 502 响应回显内部错误信息
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO42
- 作者：qihoho
- 更新时间：2026-11-07T20:45:58+08:00
### 需求摘要
**现象**
上游连接失败时，客户端收到的 502 body 包含内部错误原文，例如 `502 Bad Gateway: internal error: 101 switching protocols response with non-writable body`、dial 地址、TLS 错误等。
**原因**
`errorHandler` 直接把 `err.Error()` 写回客户端。
相关代码：`synap-agent/agent-gateway/proxy.go:254`。
**影响**
泄露内部实现、上游地址、DNS/TLS 细节，可能被用于探测内网拓扑。
**修复建议**
- 客户端只返回通用 `502 Bad Gateway`（可带 request id）；
- 详细错误写结构化日志/指标；
- 保持 Content-Type 与统一错误 JSON 格式。
**验收标准**
- 客户端响应不包含内部错误细节；
- 日志中仍能定位具体失败原因；
- 新增 502 脱敏测试。
已复现：升级失败时客户端收到内部错误原文。
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## gitee#IKTO41 — [P2] agent-gateway: 管理端点未鉴权且泄露 broker/topic/spool 路径
- 状态：closed（未处理）
- 标签：无
- 原始链接：https://gitee.com/coolxer-studio/synap/issues/IKTO41
- 作者：qihoho
- 更新时间：2026-11-07T20:45:55+08:00
### 需求摘要
**现象**
`/__agent-gateway/health` 与 `/__agent-gateway/stats` 与业务流量共用监听端口，且没有任何鉴权。`/stats` 会返回 Kafka broker 列表、topic、spool 文件路径和采集计数。
**原因**
`serveInternal` 在 `ServeHTTP` 最前面按固定路径拦截并直接返回 200；没有来源限制、token 或独立监听地址。
相关代码：`synap-agent/agent-gateway/proxy.go:338`。
**验证**
匿名请求 `/__agent-gateway/stats` 实测返回 200，并包含 `kafka.brokers`、`kafka.topic`、`spool_file`。
**影响**
在公网或共享网络中会泄露内部拓扑与运行状态；攻击者也可据此判断 Kafka/落盘是否异常。
**修复建议**
- 增加独立 admin 监听地址（如 `127.0.0.1:9090` 或单独端口）；
- 或至少支持 token/来源 IP 白名单，并默认关闭 stats；
- `/health` 保持轻量，`/stats` 需要鉴权；
- 内部路径前缀可配置，避免与上游真实路由冲突。
**验收标准**
- 未授权访问 stats 返回 401/403 或不可达；
- 业务端口…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果
