# Agent 接入

选择 Agent 时先确认运行平台、身份注册方式、采集边界和回滚手段。所有生产 Agent 应使用安全通信协议 v1；业务 method、逻辑路径、受保护 header 和正文都位于密文中。

## 接入选择

| 目标 | 入口 | 建议先读 |
| --- | --- | --- |
| 浏览器 / WebView | `synap-agent/agent-h5` | [Web / H5 接入](Web-H5接入.md) |
| Electron 桌面应用 | `synap-agent/agent-electron` | [Electron 接入](Electron接入.md) |
| Linux / Windows / macOS | `synap-agent/agent-host` | [Host 接入与运维](Host接入与运维.md) |
| Qt 5.15/6.x 桌面应用 | `synap-agent/agent-qt` | [Qt 桌面端点](Qt桌面端点.md) |
| Spring Boot 应用 | `synap-agent/agent-springboot` | [Spring Boot 接入](Spring-Boot接入.md) |
| Android / iOS | `agent-android` / `agent-ios` | [Android 与 iOS](Android与iOS.md) |
| HarmonyOS NEXT | 客户端已完成，Server 待适配 | [HarmonyOS NEXT 状态](HarmonyOS-NEXT状态.md) |
| ESP32 / C3 / S3 | 客户端已完成，Server 待适配 | [ESP32 接入状态](ESP32接入状态.md) |
| 协议联调与样例 | `agent-emulator` | [数据仿真器](数据仿真器.md) |
| 微信小程序 | 当前不可用 | [微信小程序状态](微信小程序状态.md) |

## 所有接入都要确认

1. Server HTTPS Origin、key ID 和 P-256 SPKI 信任锚。
2. 协议 appId 与业务 appId 的区别；公开标识不能当作密码。
3. 安装身份和离线队列目录的持久化、权限与备份边界。
4. 业务 `messageId` 的幂等语义和 ACK 删除条件。
5. 采集字段、隐私披露、数据保留周期和最小权限。
6. 升级、观察模式、紧急停用与旧版本回退步骤。

> Agent 上报采用“至少一次传输”。只有明确收到 accepted 或 duplicate ACK 后才能删除本地记录；超时不等于 Server 未处理。

