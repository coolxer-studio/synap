# HarmonyOS NEXT 状态

`synap-agent/agent-harmony` 是 ArkTS、Stage 模型的 HarmonyOS NEXT 客户端，最低 API 12，
交付 `probe` HAR 和 `entry` 示例 HAP。客户端已实现协议 v1、HUKS 安装身份、有界持久队列、
ACK 重试和基础 Harmony Fact，但当前 Synap Server 尚未注册 `HARMONY/harmony`。

> **当前不可连接生产 Server。** SDK 不会伪装成 Android，也不会回退明文端点；开发阶段
> 只能使用明确支持 `HARMONY/harmony` 的协议 mock。

## 客户端能力

- `P-256 + HKDF-SHA256 + AES-256-GCM`，固定 Server SPKI 信任锚。
- HUKS 中不可导出的安装签名密钥和稳定应用级设备 ID。
- 有界持久队列、240 KiB 分批、accepted/duplicate ACK 清理和指数退避。
- `HarmonyDevice`、`HarmonySelfApp`、`HarmonyRuntime`、`HarmonyNetwork`、
  `HarmonyIntegrity`，以及宿主主动提交的 UserData/Action。
- 默认不采集位置、联系人、安装应用列表或任意文件，不持续后台运行。

宿主需声明 `INTERNET` 与 `GET_NETWORK_INFO`，在前台调用 `start()`，进入后台调用
`stop({ flush: true })`。完整 HAR 引入、初始化、签名固定和生命周期示例见
`synap-agent/agent-harmony/README.md` 与 `doc/接入-HarmonyOS.md`。

## Server 前置条件

生产放行前必须同步完成并测试：

1. enrollment、握手授权和安装身份允许 `deviceType=HARMONY`。
2. 逻辑层允许 `common.platform=harmony`，并校验 start/upload 元数据。
3. 增加 Harmony checker、Fact 构造、类型白名单、规则和 Kafka topic。
4. 验证首次注册、错误会话重握手、重复 ACK、断网和应用重启恢复。
5. 将 Harmony 协议 appId 加入生产允许列表，并完成权限与隐私评审。

只有上述服务端契约和端到端测试完成后，兼容性矩阵才能从“Server 待适配”改为“支持”。
