# GitHub Issue 报告

- 生成时间：2026-11-07T20:59:24+08:00
- 范围：`github` 全部 open issue，已过滤 Pull Request。
- 数量：12

## 处理结果

- `github#1`：已修复并通过 `6c6099c9685af4ac622d779800745efe83b81b32` 关闭。
- `github#2`–`github#12`：按用户要求不处理，已全部关闭（`not_planned`）。

## github#12 — [P1] agent-gateway: spool 回放整文件加载、整批发送、失败全量重写
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/12
- 作者：qihaha
- 更新时间：2026-11-07T12:39:57Z
### 需求摘要
**现象**
每次回放都会把 rotated spool 全量读入内存，构造一个包含所有记录的 `[]kafka.Message`，一次性 `WriteMessages`；失败后逐条重写回 spool，并删除原文件。
**原因**
`readSpoolFile` 返回全量 `[]spoolRecord`；`replayOnce` 一次性构造 `msgs` 并调用 `r.send(ctx, msgs)`；失败时在 `Spool` 互斥锁内逐条 `Write`。
相关代码：`synap-agent/agent-gateway/spool.go:187`、`spool.go:208`、`spool.go:218`。
**影响**
- spool 达到 GB 级时回放 OOM；
- 单次请求过大，容易触发 broker 限制；
- 失败回写是 O(n²) I/O，并长时间占用 spool 锁，阻塞实时写入；
- 回放成功前删除原文件，崩溃窗口内可能丢数据。
**修复建议**
- 流式读取并按 `BatchSize`/`BatchBytes` 分块发送；
- 失败时只退回当前分块，并对失败分块做二分定位；
- 使用重命名/游标记录已确认位点，避免整文件反复读写；
- 增加回放速率限制与失败退避。
**验收标准**
- 大 spool 回放内存可控；
- 单条失败不影响其他记录；
- 回放期…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：高
- 待补充信息：缺少复现步骤或运行环境；缺少预期结果

## github#11 — [P1] agent-gateway: 队列满时在请求路径同步写 spool，阻塞代理转发
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/11
- 作者：qihaha
- 更新时间：2026-11-07T12:39:54Z
### 需求摘要
**现象**
Kafka 队列满时，`Write` 直接调用 `spoolMessage` 同步写文件；磁盘慢、锁竞争或磁盘满时，请求 goroutine 会被阻塞。`console`/`file` sink 也在 `emit` 中同步写。
**原因**
`KafkaSink.Write` 的 `default` 分支调用 `spoolMessage`，最终在 `Spool.Write` 的互斥锁内执行 `os.File.Write`；`multiSink` 顺序写 console/file/Kafka，file/console 写发生在请求路径。
相关代码：`synap-agent/agent-gateway/kafka.go:72`、`kafka.go:82`、`spool.go:50`、`proxy.go:319`。
**影响**
Kafka 不可用或队列打满时，转发延迟被磁盘 I/O 放大；高并发下所有请求在 spool 互斥锁上排队，可能从“日志系统故障”演变为“网关不可用”。
**修复建议**
- 为 spool 增加独立的有界 writer goroutine 与缓冲写；
- 请求路径只做非阻塞入队，队列满时按策略丢弃并计数；
- console/file sink 改为异步或带缓冲，避免同步 I/O 阻塞转发；
- 增加 spool 队列深度和写入耗时指标。
**…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：高
- 待补充信息：缺少复现步骤或运行环境；缺少预期结果

## github#10 — [P1] agent-gateway: Kafka 队列按条数限制，缺少字节预算导致内存放大
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/10
- 作者：qihaha
- 更新时间：2026-11-07T12:39:51Z
### 需求摘要
**现象**
`queue_size=10000` 只限制消息条数，不限制总字节数。默认不截断 body 时，10,000 条大记录可能占用数十 GB 内存。
**原因**
`make(chan kafka.Message, cfg.QueueSize)` 按条数分配；`Write` 不检查 value 长度或队列总字节。
相关代码：`synap-agent/agent-gateway/kafka.go:62`、`kafka.go:72`。
**影响**
流量高峰或大 body 场景下，队列成为主要内存消耗点；OOM 时进程整体退出，影响转发本身。
**修复建议**
- 增加 `queue_bytes` 预算，入队前按 `len(key)+len(value)+overhead` 计算；
- 超预算时按策略丢弃/落 spool/降采样，并记录指标；
- 与单条大小上限配合，避免单条记录吃掉整个预算。
**验收标准**
- 队列内存有明确上限；
- 超预算行为可观测；
- 新增大 body 并发写入的压力测试。
代码审查确认。
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## github#9 — [P1] agent-gateway: 响应体无上限缓冲，长连接/大文件导致内存持续增长
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/9
- 作者：qihaha
- 更新时间：2026-11-07T12:39:47Z
### 需求摘要
**现象**
响应 body 通过 `bytes.Buffer` 累积，默认 `max_body_size=0` 不截断。大文件下载、SSE 长连接会持续占用内存，且记录要等流结束才发出。
**原因**
`exchange.body` 是 `bytes.Buffer`；`captureReadCloser` 在每次 `Read` 时追加；`emit` 只在 `ServeHTTP` 返回后执行。
相关代码：`synap-agent/agent-gateway/proxy.go:42`、`proxy.go:243`、`proxy.go:316`。
**影响**
- 单个大响应可占用数百 MB/GB；
- SSE 连接可能持续数小时，期间无法产出记录且内存只增不减；
- Kafka 记录体积不可控，进一步触发 1MB 毒丸问题。
**修复建议**
- 设置默认捕获上限，并提供按路径/Content-Type 的覆盖；
- 对 SSE/流式响应记录 `body_complete=false`、字节数、持续时间和首字节时间；
- 采用环形缓冲或达上限后停止捕获；
- 考虑按时间/大小分片或仅采样捕获。
**验收标准**
- 大响应/长连接内存有明确上限；
- 记录中包含完整性标记与流式统计；
- 新增 SSE 与大响应测试。
代码审查确认；README 已提示该风险，但默认仍为不限制。
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少复现步骤或运行环境；缺少预期结果

## github#8 — [P1] agent-gateway: 请求体在转发前被全量读入内存，默认无大小上限
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/8
- 作者：qihaha
- 更新时间：2026-11-07T12:39:44Z
### 需求摘要
**现象**
开启 body 捕获（默认）时，网关会先 `io.ReadAll` 整个请求体，再转发给上游。大文件上传会占用等量内存，且上游要等整个 body 读完才开始处理。
**原因**
`captureRequestBody` 在无 `max_body_size` 时直接 `io.ReadAll(r.Body)`；有上限时也会先读满上限再重建 `MultiReader`。服务端没有 `http.MaxBytesReader`。
相关代码：`synap-agent/agent-gateway/proxy.go:381`、`proxy.go:401`、`main.go:126`。
**影响**
- 上传 1GB 文件即占用约 1GB 内存，并发时容易 OOM；
- 请求首字节延迟显著增加；
- 破坏流式上传/慢速客户端；
- 可被用作内存 DoS。
**修复建议**
- 用 `io.TeeReader` 包装 `r.Body`，边转发边捕获，只保留上限内的字节；
- 增加 `http.MaxBytesReader` 与默认 `max_body_size`（如 256KB/1MB）；
- 记录 `body_complete`，明确 body 是否被截断；
- 在捕获缓冲区满后停止写入但仍继续转发。
**验收标准**
- 大文件上传内存占用与 body 大小无关；
- 上游可以边收…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少复现步骤或运行环境；缺少预期结果

## github#7 — [P0] agent-gateway: strip_prefix 按普通字符串前缀匹配，误裁剪相邻路径段
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/7
- 作者：qihaha
- 更新时间：2026-11-07T12:39:30Z
### 需求摘要
**现象**
配置 `strip_prefix=["/gate"]` 时，请求 `/gatewayfoo/x` 会被改写成 `/wayfoo/x`。
**原因**
`rewrite` 只做 `strings.HasPrefix(path, prefix)`，没有要求前缀结束于路径段边界。
相关代码：`synap-agent/agent-gateway/proxy.go:201`。
**影响**
所有以相同字符开头的无关路由都会被误改，可能导致 404 或请求打到错误的上游资源。多个前缀按配置顺序匹配，也会带来不确定性。
**修复建议**
- 仅匹配 `path == prefix` 或 `strings.HasPrefix(path, prefix+"/")`；
- 统一去除末尾 `/`；
- 多个前缀按最长匹配优先，或在启动时检测重叠并报错。
**验收标准**
- `/gate/x` 被裁剪，`/gatewayfoo/x` 保持原样；
- 前缀含尾斜杠、根路径等边界行为有测试；
- 新增 strip_prefix 边界回归测试。
已复现：prefix=/gate 时 /gatewayfoo/x → /wayfoo/x。
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## github#6 — [P0] agent-gateway: 转发路径丢失 URL 编码（RawPath 被清空）
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/6
- 作者：qihaha
- 更新时间：2026-11-07T12:39:27Z
### 需求摘要
**现象**
请求路径中的 `%2F`、`%20` 等编码在转发到上游后语义改变。例如 `/a%2Fb/c%20d` 到达上游变成 `/a/b/c d`。
**原因**
`rewrite` 直接使用已解码的 `in.URL.Path` 重新拼接，并把 `out.URL.RawPath` 置空。Go 会按解码后的 Path 重新编码，编码斜杠等保留字符无法保持原语义。
相关代码：`synap-agent/agent-gateway/proxy.go:207`。
**影响**
依赖编码斜杠的 API（资源 ID、对象 key、部分 REST 路由）会被路由到错误路径；`%2e%2e` 等编码还可能参与路径穿越。
**修复建议**
- 同时处理 `EscapedPath()`/`RawPath`，基于原始编码做前缀裁剪与拼接；
- 不要无条件清空 `RawPath`；
- 对拼接结果做合法性校验，避免编码后的 `..` 逃逸 target base path。
**验收标准**
- `/a%2Fb/c%20d` 原样到达上游；
- 普通路径、前缀裁剪、add_prefix 组合行为正确；
- 新增编码路径回归测试。
已复现：/a%2Fb/c%20d → /a/b/c d，RawPath 为空。
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## github#5 — [P0] agent-gateway: spool 回放遇到超长行会留下孤儿文件并永久停止回放
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/5
- 作者：qihaha
- 更新时间：2026-11-07T12:39:24Z
### 需求摘要
**现象**
spool 中只要存在一行超过 64MB，回放就会报 `bufio.Scanner: token too long`，该 rotated 文件永远不会被再次处理，也不会被清理。
**原因**
`readSpoolFile` 使用 `bufio.Scanner`，最大 token 固定为 64MB。读取报错后 `replayOnce` 只记录日志并 `return`，既不重新入 spool，也不删除 rotated 文件；此时 `Rotate` 已经把原文件改名，下一次回放只会处理新的空 spool，旧文件成为永久孤儿。
相关代码：`synap-agent/agent-gateway/spool.go:123`、`spool.go:197`。
**复现**
1. 向 spool 写入一条 65MB 记录和一条正常记录；
2. 触发 `replayOnce`；
3. 实测发送 0 条，目录中残留约 65MB 的 `spool.ndjson.replay.*`，后续回放不再处理该文件。
**影响**
无上限 body + 无上限 spool 时很容易触发；数据永久滞留、磁盘泄漏，且没有任何重试或告警指标。
**修复建议**
- 改用 `bufio.Reader.ReadBytes`/流式解码，不设 64MB 硬上限；
- 单行解析失败时记录 `SpoolCorrupt`…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：高
- 待补充信息：缺少预期结果

## github#4 — [P0] agent-gateway: 单条记录超过 1MB 后永久无法投递并阻塞 spool 回放
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/4
- 作者：qihaha
- 更新时间：2026-11-07T12:39:21Z
### 需求摘要
**现象**
当单条交易 JSON 超过 Kafka 客户端的 `BatchBytes`（默认 1MB）或 broker 的 `message.max.bytes` 时，该记录永远无法发送；实时批处理会整批落 spool，回放又反复失败。
**原因**
- 默认 `capture.max_body_size=0`（不截断），请求+响应 body 很容易超过 1MB；
- `kafka.Writer` 未配置 `BatchBytes`，kafka-go 默认 1MB；
- `KafkaSink` 发送失败时把整批消息全部写入 spool；
- `Replayer.replayOnce` 把整个 spool 文件作为一次 `WriteMessages` 发送，一条超限记录会让整批永久失败。
相关代码：`synap-agent/agent-gateway/kafka.go:106`、`kafka.go:212`、`spool.go:187`。
**复现**
1. 构造 2MB 的 message 调用 `kafka.Writer.WriteMessages`；
2. 实测返回 `MessageTooLargeError`；
3. 在网关中表现为：批次失败 → 全部 spool → 每次回放再次失败，spool 持续增长。
**影响**
单条大 body 会形成毒丸，阻塞该批次及后续所有…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：高
- 待补充信息：缺少预期结果

## github#3 — [P0] agent-gateway: gzip/br 与二进制响应体被 JSON 编码破坏
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/3
- 作者：qihaha
- 更新时间：2026-11-07T12:39:17Z
### 需求摘要
**现象**
压缩响应或二进制响应写入记录后无法还原，body 中出现大量 U+FFFD 替换字符。
**原因**
`RequestRec.Body`/`ResponseRec.Body` 是 `string`。捕获到的是上游原始字节（网关设置了 `DisableCompression: true`，不会自动解压）；非 UTF-8 字节在 `encoding/json` 序列化时被替换为 `\ufffd`，原始内容不可逆丢失。
相关代码：`synap-agent/agent-gateway/record.go:53`、`record.go:65`、`proxy.go:168`（DisableCompression）。
**复现**
1. 上游返回 `Content-Encoding: gzip` 的 JSON；
2. 客户端请求经过网关；
3. 检查记录中的 `response.body`；
4. 实测为 `\x1f\ufffd\b...`，已无法解压；二进制响应同样被替换。
**影响**
常见的浏览器/网关链路默认使用 gzip/br，导致响应体分析基本失效；图片、protobuf、文件下载等二进制 body 也会损坏。`body_size` 与实际可解析内容不一致。
**修复建议**
- 记录中增加 `body_encoding`（`utf8`/`base64`）与 `con…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## github#2 — [P0] agent-gateway: 客户端中断或流式拷贝失败时丢失整条交易记录
- 状态：closed（not_planned，未处理）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/2
- 作者：qihaha
- 更新时间：2026-11-07T12:39:14Z
### 需求摘要
**现象**
当客户端在响应传输过程中断开，或上游在响应体传输中出错时，本次请求不会产生任何 Kafka/文件记录。
**原因**
`ServeHTTP` 在 `g.proxy.ServeHTTP(rec, r)` 返回后才调用 `g.emit`。`httputil.ReverseProxy` 在拷贝响应体失败时会 panic `http.ErrAbortHandler`，panic 直接跳出 `ServeHTTP`，`emit` 永远不会执行。
相关代码：`synap-agent/agent-gateway/proxy.go:316`、`proxy.go:319`。
**复现**
1. 上游持续 flush 响应体；
2. 客户端读取少量字节后主动取消请求；
3. 等待记录落盘/Kafka；
4. 实测捕获记录数为 0。
**影响**
最需要分析的异常请求（客户端取消、上游中断、超时）恰好没有记录，且 `body_truncated` 也不会标记，无法区分“完整响应”和“传输中断”。
**修复建议**
- 用 `defer g.emit(...)` 保证 panic/提前返回时也产出记录；
- 捕获 `http.ErrAbortHandler` 后重新 panic，保持 net/http 的连接中止语义；
- 在记录中增加 `aborted`/`body_complete`/`…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果

## github#1 — [P0] agent-gateway: 默认开启 body 捕获时 WebSocket 升级返回 502
- 状态：closed（completed）
- 标签：无
- 原始链接：https://github.com/coolxer-studio/synap/issues/1
- 作者：qihaha
- 更新时间：2026-11-07T13:26:32Z
- 提交：https://github.com/coolxer-studio/synap/commit/6c6099c9685af4ac622d779800745efe83b81b32
- 验证：`go vet ./...`、`go test ./...`、`go test -race ./...` 全部通过；新增升级隧道回归测试
### 需求摘要
**现象**
开启默认配置（`capture.body=true`）时，任何 WebSocket/协议升级请求都会返回 502，而不是 101。
**原因**
`modifyResponse` 无条件把上游 101 响应的 `resp.Body` 包装成 `captureReadCloser`。`net/http/httputil.ReverseProxy` 在处理升级响应时要求 `resp.Body` 实现 `io.ReadWriteCloser`，包装后不再满足该接口，于是调用 ErrorHandler 返回 502。
相关代码：`synap-agent/agent-gateway/proxy.go:243`。
**复现**
1. 启动一个返回 `101 Switching Protocols` 的上游；
2. 网关保持 `capture.body=true`；
3. 发起升级请求；
4. 实测响应为 `HTTP/1.1 502 Bad Gateway`，Body 为 `502 Bad Gateway: internal error: 101 switching protocols response with non-writable body`；
5. 关闭 `capture.body` 后升级可成功，但记录中 `bytes_out=0`、body 为空。
**影响**
R…
### 修复范围
运行目标平台构建和测试，覆盖协议 v1、ACK、队列以及平台降级行为。
### 验收标准
按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：Agent
- 优先级：中
- 待补充信息：缺少预期结果
