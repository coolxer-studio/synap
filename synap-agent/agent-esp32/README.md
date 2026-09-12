# Synap Agent ESP32

`agent-esp32` 是 ESP-IDF 5.5 安全探针，支持 `esp32`、`esp32c3`、`esp32s3`。核心以
`synap_agent` component 交付，根工程同时是可直接烧录的 Wi-Fi STA 示例。

ESP32 使用首类 `deviceType=ESP32` 和 `platform=esp32`，不会伪装成 Host。当前 Synap
Server 尚未放行该类型；本项目自带协议 v1 客户端与本地 HTTPS 协议桩，但连接生产
Server 前仍必须完成[服务端前置条件](#服务端前置条件)。

## 构建

要求 ESP-IDF `v5.5.1`。CI 固定同版本，并分别编译三个目标：

```bash
cd agent-esp32
. "$IDF_PATH/export.sh"
idf.py set-target esp32       # 也可使用 esp32c3 / esp32s3
idf.py build
```

切换目标前执行 `idf.py fullclean`。默认分区表适用于 4 MiB Flash。`sdkconfig`、构建目录、
量产配置、证书和密钥均被忽略；`sdkconfig.defaults` 是提交的通用配置。

生产构建必须显式叠加 `sdkconfig.production.defaults` 和对应目标文件，并通过受控构建环境
提供 Secure Boot 签名密钥。例如 ESP32 ECO3+：

```bash
idf.py -D 'SDKCONFIG_DEFAULTS=sdkconfig.defaults;sdkconfig.defaults.esp32;sdkconfig.production.defaults;sdkconfig.production.defaults.esp32' \
  set-target esp32
idf.py build
```

这些配置会烧写安全相关 eFuse；不得在开发板上未经量产流程评审直接烧录。普通 CI 只构建
不烧 eFuse 的开发 defaults，量产构建应在独立签名流水线中完成。

## 组件接入

业务固件负责创建默认 event loop、Wi-Fi STA 和 SNTP。组件只观察网络状态，不修改
Wi-Fi；为使生产加密配置先被安全初始化，应在创建 event loop 后、读取 Wi-Fi 配置前调用
`synap_agent_init`：

```c
#include "synap_agent.h"

static void on_state(synap_agent_state_t state, esp_err_t error,
                     const char *detail, void *context) {
    /* 不要在回调中阻塞或记录业务正文。 */
}

void start_synap(void) {
    synap_agent_config_t config = synap_agent_default_config();
    config.state_callback = on_state;
    ESP_ERROR_CHECK(synap_agent_init(&config));
    ESP_ERROR_CHECK(synap_agent_start());
}
```

- `synap_agent_init`：读取量产配置，打开身份和队列，注册网络观察器。
- `synap_agent_start`：非阻塞创建串行 worker；网络或时间未就绪时只等待。
- `synap_agent_stop`：请求停止并按 timeout 等待，不强杀任务。
- `synap_agent_get_metrics`：获取队列、上传、重连和最近错误快照。

探针不会自动格式化无法读取的 NVS 分区，避免掩盖证据或静默丢失身份。

## 量产配置

配置位于 `provision` 分区的 `synap` namespace：

| Manifest 字段 | NVS key | 说明 |
| --- | --- | --- |
| `wifi_ssid` / `wifi_password` | `wifi_ssid` / `wifi_pass` | 示例固件 Wi-Fi |
| `server_url` / `tls_ca_pem` | `server_url` / `tls_ca` | HTTPS Origin 与私有 CA |
| `sc_app_id` | `sc_app_id` | 协议 appId，不是业务 appId |
| `server_key_id` / `server_trust_spki` | `server_kid` / `trust_spki` | P-256 Server 固定值 |
| `enrollment_token` | `enroll_token` | 每设备短时单次 token |
| `business_app_id` | `business_app` | `common[4]` 的数字 ID |
| `device_id` / `device_name` | `device_id` / `device_name` | 每设备 UUID 和资产名称 |

生成和烧录见 [provisioning/README.md](provisioning/README.md)。token 只在握手开始时进入
内存，只有 `/sc/v1/handshake/finish` 成功后才从 NVS 删除；日志不输出 token、密钥、
正文或密文。

安装 P-256 私钥和跨重启业务 sequence 位于 `runtime`；`spool` 保存完整待发 envelope；
永久拒绝进入 `dead_letter`。启用 NVS Encryption 时，组件只读取既有 `nvs_keys`，不会在
设备上静默生成或替换量产密钥。生产设备必须启用 Flash Encryption、NVS Encryption 和 Secure Boot。
开发固件可在未启用时运行，但 `SecurityData` 会明确上报降级状态。

## 数据与可靠性

`common` 固定 24 项，`common[8]=esp32`。不访问第三方公网 IP 服务，由 Server 根据受控
连接来源补充。每轮采集：

- `DeviceData`：芯片、核心/特性、Flash/PSRAM、固件、ESP-IDF、重启原因。
- `SecurityData`：Secure Boot、Flash Encryption、调试/JTAG 状态、运行分区、固件
  SHA-256 和回滚配置。
- `HealthData`：运行时长、堆、Wi-Fi、队列、丢弃、上传和错误指标。

三类 Fact 使用 `schemaVersion=1`、显式命名 `data` 和稳定
`messageId/observedAt/sequence`。Start sequence 固定为 1，Fact 从 2 开始。

队列默认 128 条、单条 32 KiB。满队列拒绝最新记录，不覆盖未确认记录。重试只更新
`attempt`；每次传输重新生成 `seq/requestId/nonce`。仅 accepted/duplicate ACK 删除，
retryable 保留，永久拒绝进入最多 16 条的 dead-letter 环。

Start 必须返回五项 `config.startConfig`：启用、采集频率、采集窗口、上传频率、上传
窗口，单位秒并允许小数。缺失、禁用、非有限或非正窗口会进入永久配置错误。

## 安全通信

组件实现 protocol v1：`P-256 + HKDF-SHA256 + AES-256-GCM`、安装身份证明、Server
SPKI 固定、方向隔离密钥、nonce/AAD、响应认证和重放 sequence。外层只访问：

```text
POST /sc/v1/handshake
POST /sc/v1/handshake/finish
POST /sc/v1/message
```

逻辑 `/attach/start`、`/attach/upload`、`code` 和正文位于密文内。生产只允许 HTTPS
并正常验证 CA；`CONFIG_SYNAP_ALLOW_INSECURE_HTTP` 仅供封闭开发环境，且不会关闭应用层
加密。没有明文、旧协议或忽略证书的回退。

agenda/punish 不触发任何动作。本探针不会执行服务端代码、重启、OTA 或 GPIO 控制。

## 本地 HTTPS 协议桩

```bash
python -m pip install -r test/requirements-test.txt
mkdir -p test/generated
openssl req -x509 -newkey rsa:2048 -nodes -days 7 \
  -keyout test/generated/tls.key -out test/generated/tls.crt \
  -subj '/CN=192.168.1.10' -addext 'subjectAltName=IP:192.168.1.10'

python test/mock_server.py --cert test/generated/tls.crt \
  --tls-key test/generated/tls.key \
  --identity-key test/generated/server-identity.pem --print-trust-anchor
```

把输出 SPKI、TLS 证书和 Server 地址写入未提交的设备 manifest，然后启动协议桩：

```bash
export SYNAP_MOCK_ENROLLMENT_TOKEN='<与设备 manifest 相同的随机测试 token>'
python test/mock_server.py --cert test/generated/tls.crt \
  --tls-key test/generated/tls.key \
  --identity-key test/generated/server-identity.pem --app-id synap-esp32-dev
```

`--ack-mode` 可选 accepted、duplicate、retryable、permanent。桩的安装与重放状态只在
进程内，不能用于生产。无硬件测试运行 `python -m pytest -q test`。

协议密码学、身份恢复、NVS 队列和 ACK 的设备端 Unity 固件可单独构建烧录：

```bash
idf.py -C test/firmware set-target esp32s3
idf.py -C test/firmware build flash monitor
```

硬件验收需在 ESP32-DevKitC、ESP32-C3-DevKitM-1、ESP32-S3-DevKitC-1 上验证首次注册、
断网入队、冷重启恢复、duplicate ACK、retryable 退避、永久拒绝和最终排空。

## 服务端前置条件

生产接入前，服务端必须：

1. 在 enrollment、握手授权和安装身份中允许 `deviceType=ESP32`。
2. 允许 `platform=esp32` 和加密后的 `/attach/start`、`/attach/upload`。
3. 注册 `Esp32DeviceFact`、`Esp32SecurityFact`、`Esp32HealthFact`，校验本文
   `schemaVersion=1` 字段和大小。
4. 下发五项 `startConfig` 并保持现有 batch/message ACK 契约。
5. 只在持久化或发布成功后返回 accepted/duplicate。

这些 Server 变更不属于本项目交付范围。

## 明确不支持

首版不包含 BLE/SoftAP 配网、以太网、环境传感器、深度睡眠、远程 OTA、远程控制或通用
业务事件 API。
