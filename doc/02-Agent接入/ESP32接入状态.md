# ESP32 接入状态

`synap-agent/agent-esp32` 是 ESP-IDF 5.5.1 客户端，支持 `esp32`、`esp32c3`、`esp32s3`，
以 `synap_agent` component 和可烧录 Wi-Fi STA 示例交付。客户端使用首类
`deviceType=ESP32`、`platform=esp32`，当前 Synap Server 尚未放行这些标识，也没有对应
checker、Fact 和规则。

> **当前不可连接生产 Server。** 工程自带 HTTPS 协议桩和无硬件测试，用于在服务端适配
> 前验证客户端；不得把 mock 或开发 NVS 配置用于生产。

## 构建与量产边界

```bash
cd synap-agent/agent-esp32
. "$IDF_PATH/export.sh"
idf.py set-target esp32  # 或 esp32c3 / esp32s3
idf.py build
python -m pytest -q test
```

生产构建必须通过受控签名流水线叠加 `sdkconfig.production.defaults` 和对应芯片配置，启用
Flash Encryption、NVS Encryption 与 Secure Boot。每台设备使用独立 UUID、短时单次
enrollment token 和受控 manifest；真实 manifest、NVS key、生成 CSV/镜像不得进入 Git。

组件不会配置 Wi-Fi，业务固件应先创建 event loop，并在读取 Wi-Fi 配置前调用
`synap_agent_init`。身份、业务 sequence、spool 和 dead-letter 位于独立 NVS 分区；端点
不会自动格式化损坏分区，也不会静默替换量产密钥。

## 数据与可靠性

- `DeviceData`：芯片、核心、Flash/PSRAM、固件、ESP-IDF 和重启原因。
- `SecurityData`：Secure Boot、Flash Encryption、JTAG、运行分区、固件摘要和回滚配置。
- `HealthData`：运行时长、堆、Wi-Fi、队列、丢弃、上传和错误指标。
- 默认队列 128 条、单条 32 KiB；满队列拒绝新记录，不覆盖未确认记录。
- accepted/duplicate 才删除，retryable 保留，永久拒绝进入最多 16 条的 dead-letter。

## Server 前置条件

1. 允许 `deviceType=ESP32`、`platform=esp32` 与加密 start/upload。
2. 注册 `Esp32DeviceFact`、`Esp32SecurityFact`、`Esp32HealthFact` 及类型白名单。
3. 下发五项 `startConfig`，保持现有 batch/message ACK 契约。
4. 只在持久化或发布成功后返回 accepted/duplicate。
5. 完成三种目标板上的首次注册、断网、冷启动、duplicate、retryable、permanent 和排空验收。

详细构建、分区、协议桩、烧录和量产配置见 `synap-agent/agent-esp32/README.md` 与
`provisioning/README.md`。
