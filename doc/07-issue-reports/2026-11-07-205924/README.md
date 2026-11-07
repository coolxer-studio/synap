# Issue 归纳报告 — 2026-11-07 20:59:24 +0800

本报告由 `$synap-issue-workflow` 的 `report` 模式生成，仅包含脱敏后的需求摘要。
报告生成阶段不会修改代码、标签或 issue 状态。

## 审核方式

1. 删除整个 issue 标题块表示不执行该 issue。
2. 修改“修复范围”和“验收标准”表示按修改后的内容执行。
3. 编辑完成后，在对话中明确说“按这份报告修复”，才会进入执行阶段。

## 汇总

| 平台 | 数量 | 分类 |
| --- | ---: | --- |
| github | 12 | 缺陷 12 |
| gitee | 15 | 缺陷 15 |

## 分平台报告

- [GitHub](github.md)
- [Gitee](gitee.md)

## 处理结果

- GitHub：`github#1` 已修复并关闭；`github#2`–`github#12` 按用户要求未处理，已全部关闭。
- Gitee：`gitee#IKTO41`–`gitee#IKTO4F` 按用户要求未处理，已全部关闭。
- 复核：GitHub open issue 为 0；Gitee 显示「开启的 0 / 已关闭 15」。

## 执行清单

下列清单仅用于概览；实际删减必须在 `github.md` 或 `gitee.md` 中完成，脚本只解析这两个平台文件。

- [x] github#12 — [P1] agent-gateway: spool 回放整文件加载、整批发送、失败全量重写（已关闭，未处理）
- [x] github#11 — [P1] agent-gateway: 队列满时在请求路径同步写 spool，阻塞代理转发（已关闭，未处理）
- [x] github#10 — [P1] agent-gateway: Kafka 队列按条数限制，缺少字节预算导致内存放大（已关闭，未处理）
- [x] github#9 — [P1] agent-gateway: 响应体无上限缓冲，长连接/大文件导致内存持续增长（已关闭，未处理）
- [x] github#8 — [P1] agent-gateway: 请求体在转发前被全量读入内存，默认无大小上限（已关闭，未处理）
- [x] github#7 — [P0] agent-gateway: strip_prefix 按普通字符串前缀匹配，误裁剪相邻路径段（已关闭，未处理）
- [x] github#6 — [P0] agent-gateway: 转发路径丢失 URL 编码（RawPath 被清空）（已关闭，未处理）
- [x] github#5 — [P0] agent-gateway: spool 回放遇到超长行会留下孤儿文件并永久停止回放（已关闭，未处理）
- [x] github#4 — [P0] agent-gateway: 单条记录超过 1MB 后永久无法投递并阻塞 spool 回放（已关闭，未处理）
- [x] github#3 — [P0] agent-gateway: gzip/br 与二进制响应体被 JSON 编码破坏（已关闭，未处理）
- [x] github#2 — [P0] agent-gateway: 客户端中断或流式拷贝失败时丢失整条交易记录（已关闭，未处理）
- [x] github#1 — [P0] agent-gateway: 默认开启 body 捕获时 WebSocket 升级返回 502（已提交 6c6099c，已关闭）
- [x] gitee#IKTO4F — [P4] agent-gateway: Kafka 默认值与回放事件时间不合理（已关闭，未处理）
- [x] gitee#IKTO4E — [P4] agent-gateway: 记录 schema 缺少完整性/编码/追踪字段，ID 仅 64-bit（已关闭，未处理）
- [x] gitee#IKTO4D — [P4] agent-gateway: 缺少可信代理支持，client_ip/scheme 在 LB 后失真（已关闭，未处理）
- [x] gitee#IKTO4C — [P4] agent-gateway: 缺少采样、路径过滤、仅错误捕获与 header 白名单（已关闭，未处理）
- [x] gitee#IKTO4B — [P3] agent-gateway: 关键场景测试缺失，代码全部集中在 package main（已关闭，未处理）
- [x] gitee#IKTO4A — [P3] agent-gateway: Docker/CI/依赖与基础镜像过期，TLS 下 healthcheck 失效（已关闭，未处理）
- [x] gitee#IKTO49 — [P3] agent-gateway: 优雅关闭存在竞态，可能丢记录或写入已关闭资源（已关闭，未处理）
- [x] gitee#IKTO48 — [P3] agent-gateway: 指标与健康检查不足，多副本无法归因（已关闭，未处理）
- [x] gitee#IKTO47 — [P3] agent-gateway: 配置校验缺口会导致启动 panic、忙循环或静默关闭限制（已关闭，未处理）
- [x] gitee#IKTO46 — [P2] agent-gateway: TLS 缺少最低版本/mTLS，cert 或 key 半配置时静默降级为 HTTP（已关闭，未处理）
- [x] gitee#IKTO45 — [P2] agent-gateway: target userinfo 与 Kafka SASL 密码等 secret 处理不安全（已关闭，未处理）
- [x] gitee#IKTO44 — [P2] agent-gateway: 缺少入站认证、限流、请求体上限与 body 读取超时（已关闭，未处理）
- [x] gitee#IKTO43 — [P2] agent-gateway: 默认全量捕获 body，但只脱敏 header，敏感数据可进入 Kafka/磁盘（已关闭，未处理）
- [x] gitee#IKTO42 — [P2] agent-gateway: 502 响应回显内部错误信息（已关闭，未处理）
- [x] gitee#IKTO41 — [P2] agent-gateway: 管理端点未鉴权且泄露 broker/topic/spool 路径（已关闭，未处理）
