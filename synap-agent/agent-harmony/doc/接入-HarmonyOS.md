# HarmonyOS NEXT 接入检查表

1. 使用 API 12+ 的 Stage 模型工程，并引入 `@coolxer/synap-agent-harmony` HAR。
2. 声明 `INTERNET` 与 `GET_NETWORK_INFO`，不需要位置、联系人或文件权限。
3. 通过可信配置渠道提供 `appId`、HTTPS 服务地址和 `kid -> P-256 SPKI Base64URL`。
4. 新安装通过业务后端取得短时、单次 enrollment token；不要写入 Preferences、日志
   或 UI 状态持久化。
5. 在前台生命周期调用 `start`，进入后台调用 `stop({ flush: true })`。
6. 仅提交业务允许的低敏感 `userData`；不得以改名方式绕过 SDK 的敏感字段检查。
7. 上线前确认服务端已同时支持安全通信 `deviceType=HARMONY`、业务
   `common.platform=harmony`、Harmony Fact 与 checker 配置。
8. 验证签名固定、错误会话重握手、ACK 重复消费、断网重试和应用重启恢复。

## 服务端前置条件

当前仓库服务端未实现第 7 项，因此生产请求会在握手或业务平台注册阶段失败。SDK 不
会回退明文端点，也不会把 HarmonyOS 设备报告成 Android。开发阶段应使用接受
`HARMONY/harmony` 的协议 mock。

