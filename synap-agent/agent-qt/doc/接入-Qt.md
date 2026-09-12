# Qt 桌面探针接入

## 适用边界

Qt 探针嵌入业务桌面进程，复用服务端 `host` 平台和 Host Fact。它适合需要关联桌面
应用会话、设备环境和业务 UserData 的场景；不替代具有系统级文件、进程、端口与审计
能力的 `agent-host`。同一设备可以部署两者，但应使用不同 `businessAppId` 或明确接受
两条独立会话，避免风险视图重复。

## 配置项

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `baseUrl` | 是 | Server HTTPS Origin，可包含受控基础路径，不可含账号、query 或 fragment |
| `businessAppId` | 是 | 正整数，写入 24 项 common 和 `{guid}-host-{appId}-1.0.0` |
| `secureAppId` | 是 | 握手公开标识，与注册令牌的授权范围一致 |
| `serverKeyId` | 是 | 固定 Server 身份 key ID |
| `trustAnchorBase64Url` | 是 | 未填充的 P-256 SPKI Base64URL |
| `appName/appPackage/appVersion` | 是 | 宿主应用资产标识 |
| `enrollmentTokenProvider` | 首次安装 | 返回短时单次注册令牌；调用后 SDK 会丢弃 provider |
| `dataDirectory` | 否 | 身份和队列目录；默认使用 AppLocalDataLocation |

不要把注册令牌、私钥或可轮换 Secret 编译进可执行文件。Server key ID 与公钥信任锚
属于公开信任配置，可以随应用发布，但轮换时应通过应用升级同时携带新配置。

## QML 暴露

```cpp
engine.rootContext()->setContextProperty("synapAgent", agent);
```

```qml
Connections {
    target: synapAgent
    function onReminderReceived(title, message) {
        notificationModel.append({title, message})
    }
    function onErrorOccurred(code, message, retryable) {
        console.warn(code, message, retryable)
    }
}
```

SDK 不链接 QtQml，也不注册全局 QML URI。这样不会改变宿主的 QML import、插件目录或
部署工具行为。

## 数据与调度

启动后先通过加密 `/attach/start` 获取 Host checker 配置。`startConfig` 的采集间隔、
采集窗口、上传间隔和上传窗口控制本次会话。SDK 只生成配置中声明列的数组，并保持顺序：

- `DeviceData`：系统、CPU、屏幕、磁盘、GUID、基础网络；敏感硬件标识为空。
- `RuntimeData`：只匹配当前应用进程；env 和 cmd 列为空。
- `NetData`：活动接口和不含凭据的系统代理；端口、socket、UDS、hosts、证书为空。
- `UserData`：由宿主显式提交。

每条 Fact 在入队时生成稳定的 `messageId/observedAt/sequence`。重试只增加业务 attempt，
并为每次安全传输生成新的 seq/requestId 和密文。单条业务记录超过 256 KiB 会在本地拒绝。

## 退出、升级与排障

应用退出前调用 `stop()`；SDK 最多使用 5 秒尝试冲刷现有队列，未确认记录保留到下次
启动。升级和卸载时不要删除数据目录，否则 Server 会看到新的安装身份，离线队列也会丢失。

常见错误：

| code | 处理 |
| --- | --- |
| `AGENT_CONFIG_INVALID` | 检查 HTTPS、两个 appId、key ID 和 SPKI 格式 |
| `SC_HANDSHAKE_FAILED` | 检查注册令牌范围、Server key 轮换、客户端时钟 |
| `SC_UNKNOWN_SESSION` | 当前请求失败并保留队列；下一次请求重新握手 |
| `SC_NETWORK_FAILED` | 检查 TLS、代理、DNS；SDK 自动退避，禁止改成明文 |
| `AGENT_ACK_INVALID` | 保留原 messageId，检查 Server 版本和 ACK batchId |
| `AGENT_QUEUE_FAILED` | 检查数据目录权限、磁盘空间与安全软件拦截 |

日志中只记录错误 code、通用消息和 retryable 状态；宿主也不应打印令牌、私钥、完整
业务 UserData 或加密前报文。

