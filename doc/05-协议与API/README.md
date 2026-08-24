# 协议与 API

本章用于联调和二次开发。生产接入应优先使用各平台 Agent SDK，而不是自行拼装加密信封或直接调用逻辑 Controller。

## 接口分层

| 层次 | 内容 | 使用者 |
| --- | --- | --- |
| 安全传输层 | `/sc/v1/handshake`、`/finish`、`/message` | Agent SDK |
| Server 逻辑层 | `/attach/start`、`/attach/upload`、`/cross/{id}` | 仅由安全层解密后转发 |
| Fact 与规则层 | checker、Fact、Agenda、Punish、Checkpoint | Server 和规则开发者 |
| Build API | `/api/v1/templates`、`/api/v1/tasks*`、SSE | Synap Build 页面或受控客户端 |

## 阅读导航

- [安全通信协议 v1](安全通信协议-v1.md)
- [Server API](Server-API.md)
- [Fact 与规则模型](Fact与规则模型.md)
- [Synap Build API](Synap-Build-API.md)

完整字段定义的事实来源仍是 `synap-server/doc/API.md` 与 `synap-build/internal/assets/openapi.yaml`；集中站点提供推荐入口和运维所需摘要。
