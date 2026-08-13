# Synap Agent Host

`agent-host` 主机探针以独立进程运行，支持 Linux、Windows、macOS 的 amd64/arm64。它从 synap-server 获取 Host checker 配置，按配置采集主机事实，并通过有界落盘队列可靠上报。

`AuditData` 审计监控在探针整个进程生命周期持续运行，不受 `startConfig` 快照采集窗口限制。它覆盖设备启停、登录/登出、账户权限、关键配置、USB、网络、进程、业务文件和计划任务。探针只读预先部署的系统审计设施；缺少权限或组件时发送 `capability/degraded`，不会伪造事件或自动修改审计策略。

## 构建与测试

要求 Go 1.23+：

```bash
go test ./...
go test -race ./...
./build.sh linux amd64
```

六目标无 CGO 编译检查：

```bash
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./...
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build ./...
CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build ./...
CGO_ENABLED=0 GOOS=windows GOARCH=arm64 go build ./...
CGO_ENABLED=0 GOOS=darwin GOARCH=amd64 go build ./...
CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 go build ./...
```

synap-build 会固化默认服务地址、协议 appId、服务端 keyId 和公开信任锚。运行时
仍可用参数或环境变量覆盖；服务端私钥和注册令牌不会进入二进制。

## 运行

```bash
./x-probe --server http://127.0.0.1:11099 --app-id 1
./x-probe data -i user-1 -d '{"scene":"login"}'
./x-probe device
./x-probe rating
./x-probe label
```

配置优先级为命令行、环境变量、编译值、安全默认值。主要参数：

- `--server` / `SYNAP_HOST_SERVER`
- `--public-ip-url` / `SYNAP_HOST_PUBLIC_IP_URL`（默认使用 `https://api64.ipify.org?format=json`）
- `--app-id` / `SYNAP_HOST_APP_ID`
- `--app-name`、`--app-package`、`--app-version`、`--user-id`
- `--data-dir`、`--log-level`
- `--spool-max-bytes`、`--spool-max-items`
- `SYNAP_SC_APP_ID`（协议 appId，不等于业务数字 `--app-id`）
- `SYNAP_SC_SERVER_KEY_ID`
- `SYNAP_SC_TRUST_ANCHOR` 或 `SYNAP_SC_SERVER_PUBLIC_KEY`（P-256 SPKI Base64URL）
- `SYNAP_SC_ENROLLMENT_TOKEN`（仅首次安装需要的短时单次令牌）

默认 spool 上限为 100 MiB、10,000 条、7 天。队列和会话文件仅当前运行账号可访问。每条
消息在入队时获得稳定 `messageId/batchId/observedAt/sequence`，StartData 为 sequence 1，
普通消息从 2 开始；跨进程 sequence 分配由文件锁保护。只有服务端 accepted/duplicate ACK
才删除记录，上传失败会退避重试，永久拒绝进入 `dead-letter`。`data` 命令不再启动本地
HTTP 服务，而是向运行中探针的共享 spool 原子写入。

审计游标、首次基线、对象快照和 clean-shutdown 标记保存在 `<data-dir>/audit-state/state.json`。目录权限为 `0700`、文件为 `0600`。首次运行只建立基线；后续从游标和快照增量采集。文件内容不会上传：64 MiB 以内流式计算 SHA-256，超过阈值只发送元数据和 `digestSkipped=size_limit`。命令行及详情中的 password/token/secret 等字段会脱敏。

新会话建立前会以 4 秒超时查询一次公网 IP，支持 `{"ip":"..."}`、`{"query":"..."}`、`{"address":"..."}` 或纯文本响应。只接受合法公网 IPv4/IPv6；查询失败时不阻断启动，由服务端连接来源地址兜底。

三平台审计策略、服务模板和能力检查见 `deploy/linux`、`deploy/windows`、`deploy/macos`；详细协议、采集字段和部署建议见 `doc/`。

## 本地联调

### 1. 启动 synap-server

synap-server 要求 JDK 17，并需要可访问的 Kafka；生产环境还需要 Redis。进入服务端项目：

```bash
cd ../../synap-server
export SYNAP_SC_ALLOWED_APP_IDS='synap-h5-dev,synap-host-dev'
# 先执行 openssl rand -hex 32，并把结果安全地复制到两个联调终端。
export SYNAP_SC_ENROLLMENT_ADMIN_TOKEN='<本次本地联调生成的随机管理令牌>'
./run.sh
```

默认监听 `11099`，可用以下命令确认：

```bash
lsof -nP -iTCP:11099 -sTCP:LISTEN
```

如果 Kafka 不可用，服务端无法确认 Kafka 发布，agent-host 会将数据保留在 spool 并持续退避重试。

### 2. 启动 agent-host

首次安装先在另一个终端签发 10 分钟有效的一次性注册令牌，并读取服务端开发
公钥作为 Host 信任锚：

```bash
cd ../../synap-server
export SYNAP_SC_ENROLLMENT_ADMIN_TOKEN='<与启动 server 时完全相同的管理令牌>'

export SYNAP_SC_ENROLLMENT_TOKEN="$(
  curl --fail --silent --show-error \
    -H "X-Synap-Admin-Token: ${SYNAP_SC_ENROLLMENT_ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d '{"appId":"synap-host-dev","deviceType":"HOST","expiresInSeconds":600}' \
    http://127.0.0.1:11099/admin/sc/v1/enrollment-tokens \
  | jq -r '.token'
)"

export SYNAP_SC_SERVER_PUBLIC_KEY="$(
  openssl pkey -pubin -in .local/sc-dev/server-public.pem -outform DER \
  | openssl base64 -A | tr '+/' '-_' | tr -d '='
)"
```

`SYNAP_SC_ENROLLMENT_ADMIN_TOKEN` 必须与启动 server 的终端使用同一个值；可由开发
人员通过安全的本地方式传递，不要写入仓库。

```bash
cd ../synap-agent/agent-host

export SYNAP_SC_APP_ID=synap-host-dev
export SYNAP_SC_SERVER_KEY_ID=server-key-dev

go run . \
  --server http://localhost:11099 \
  --app-id 1 \
  --app-name x-probe \
  --app-package agent-host \
  --app-version 2.0.0 \
  --data-dir /tmp/synap-agent-host-local \
  --log-level debug
```

首次握手成功后，v2 安装身份保存在
`/tmp/synap-agent-host-local/secure-communication/identity-v2.json`。后续运行不再需要
注册令牌，可删除 `SYNAP_SC_ENROLLMENT_TOKEN`；不要删除或复制身份文件。
Unix 会强制目录 `0700`、文件 `0600`；Windows 会设置受保护 DACL，只允许当前
运行账号访问。

从 secure-communication-go 1.x 升级时，旧 `identity.json` 不会被读取、覆盖或删除。
升级后的首次启动必须通过部署系统提供新的单次 `SYNAP_SC_ENROLLMENT_TOKEN`，成功注册
并生成 `identity-v2.json` 后再移除该环境变量。

输出 `探针已启动，进程ID: ...` 表示已经完成 `/attach/start` 并进入采集循环。另开终端检查日志和队列：

```bash
tail -f /tmp/synap-agent-host-local/logs/probe.log

find /tmp/synap-agent-host-local/spool -name '*.json' | wc -l
find /tmp/synap-agent-host-local/dead-letter -name '*.json' | wc -l
```

正常运行时 spool 可能短暂出现文件，服务端返回 HTTP 2xx 且 `status=1` 后会归零；dead-letter 应保持为 0。

### 3. 命令行验证

守护进程运行期间，可从另一个终端向同一数据目录写入自定义事件：

```bash
go run . \
  --server http://localhost:11099 \
  --data-dir /tmp/synap-agent-host-local \
  data -i test-user -d '{"action":"local-test"}'
```

查询设备、风险评级和当前启动会话标签：

```bash
go run . --server http://localhost:11099 --data-dir /tmp/synap-agent-host-local device
go run . --server http://localhost:11099 --data-dir /tmp/synap-agent-host-local rating
go run . --server http://localhost:11099 --data-dir /tmp/synap-agent-host-local label
```

## 构建发布

`build.sh` 每次生成一个目标平台的 `x-probe`，Windows 生成 `x-probe.exe`：

```bash
./build.sh linux amd64
./build.sh linux arm64
./build.sh windows amd64
./build.sh windows arm64
./build.sh darwin amd64
./build.sh darwin arm64
```

后一次构建会覆盖前一次同名产物，发布流水线应在每次构建后立即将产物复制到对应平台目录。

使用 synap-build 时，它会从 `synap-agent/agent-host` 构建，并通过以下 ldflags 固化默认服务地址：

```text
-X agent-host/transport.host=<server-origin>
-X agent-host/transport.secureAppID=<security-app-id>
-X agent-host/transport.secureServerKeyID=<server-key-id>
-X agent-host/transport.secureTrustAnchor=<P-256-SPKI-Base64URL>
```

运行时仍可通过 `--server`、`SYNAP_HOST_SERVER` 和公开的 `SYNAP_SC_*` 配置覆盖
编译值。正式发布优先使用 synap-build 固定值，避免信任配置漂移。

## Linux 生产部署

完整系统审计需要读取 auditd、journald、`/proc` 和 `/sys`，建议以 root 系统服务运行。普通权限也可以运行，但无法访问的能力会以上报 `capability/degraded` 的方式明确降级。

安装二进制和数据目录：

```bash
sudo install -m 0755 x-probe /usr/local/sbin/x-probe
sudo install -d -m 0700 /var/lib/synap-host
```

通过受控部署流程安装 auditd 规则；agent-host 自身不会修改审计策略：

```bash
sudo install -m 0600 \
  deploy/linux/99-synap-host.rules \
  /etc/audit/rules.d/99-synap-host.rules

sudo augenrules --load
sudo auditctl -l
```

将 `deploy/linux/synap-host.service` 中的服务地址替换为生产 HTTPS origin，然后安装并启动：

```bash
sudo install -m 0644 \
  deploy/linux/synap-host.service \
  /etc/systemd/system/synap-host.service

sudo systemctl daemon-reload
sudo systemctl enable --now synap-host
sudo systemctl status synap-host
```

查看运行日志：

```bash
sudo journalctl -u synap-host -f
sudo tail -f /var/lib/synap-host/logs/probe.log
```

## Windows 生产部署

先按照 `deploy/windows/audit-policy.md` 启用 Security、Task Scheduler 和 PnP 日志，并通过组织的受控流程部署 `deploy/windows/Sysmon-Synap.xml`。agent-host 不会自行安装 Sysmon 或修改 auditpol。

以管理员 PowerShell 安装服务：

```powershell
New-Item -ItemType Directory -Force 'C:\Program Files\Synap'
New-Item -ItemType Directory -Force 'C:\ProgramData\SynapHost'
Copy-Item .\x-probe.exe 'C:\Program Files\Synap\x-probe.exe' -Force

New-Service `
  -Name SynapAgentHost `
  -BinaryPathName '"C:\Program Files\Synap\x-probe.exe" --server https://synap.example.com --data-dir "C:\ProgramData\SynapHost"' `
  -StartupType Automatic

Start-Service SynapAgentHost
Get-Service SynapAgentHost
```

查看探针日志：

```powershell
Get-Content C:\ProgramData\SynapHost\logs\probe.log -Wait
```

完整审计建议使用 LocalSystem；如果使用专用服务账号，应只授予所需 Event Log 和业务目录的读取权限，以及数据目录的读写权限。

## macOS 生产部署

完整系统审计需要 root LaunchDaemon、OpenBSM、eslogger 和 Full Disk Access。相关能力检查见 `deploy/macos/README.md`。

安装二进制和数据目录：

```bash
sudo install -m 0755 x-probe /usr/local/sbin/x-probe
sudo mkdir -p '/Library/Application Support/SynapHost'
sudo chmod 700 '/Library/Application Support/SynapHost'
```

将 `deploy/macos/com.synap.agent-host.plist` 中的服务地址替换为生产 HTTPS origin，然后安装：

```bash
sudo cp deploy/macos/com.synap.agent-host.plist \
  /Library/LaunchDaemons/com.synap.agent-host.plist

sudo chown root:wheel /Library/LaunchDaemons/com.synap.agent-host.plist
sudo chmod 600 /Library/LaunchDaemons/com.synap.agent-host.plist
sudo launchctl bootstrap system /Library/LaunchDaemons/com.synap.agent-host.plist
```

检查状态和日志：

```bash
sudo launchctl print system/com.synap.agent-host
sudo tail -f '/Library/Application Support/SynapHost/logs/probe.log'
```

## synap-server 生产运行

服务端 Docker Compose 文件位于 `synap-server/deploy/docker-compose.yml`。在仓库根目录执行：

```bash
cd synap-server/deploy

docker compose --env-file .env config
docker compose --env-file .env up -d
docker compose ps
docker compose logs -f synap-server
```

生产运行必须保证：

- Kafka 地址正确，`host_fact_audit` topic 可用。
- Redis 可用，用于 Host AuditData 批次幂等及其他协议状态。
- checker、Groovy rule、punish 和 msg API 配置目录正确挂载。
- agent-host 使用 HTTPS origin；HTTP 仅用于本机联调。
- 只有真实网关 CIDR 配置到 `SYNAP_TRUSTED_PROXIES`，不得使用 `0.0.0.0/0`。

审计 Kafka topic 为 `host_fact_audit`，消息 key 是 `<guid>:<batchId>`，使用 delete 策略并默认保留 180 天。下游应以 `eventId` 做事件级最终去重。

## 配置核心业务目录

默认 `auditPolicy.businessDirectories` 为空，因此必须由部署方在 synap-server 的 `deploy/open_config/checker_config/host.json` 中配置核心业务目录，例如：

```json
"businessDirectories": [
  {
    "path": "/opt/my-service",
    "recursive": true,
    "exclude": ["*.log", "*.tmp", ".git"],
    "followSymlinks": false
  }
]
```

修改前先校验 JSON：

```bash
jq empty deploy/open_config/checker_config/host.json
```

synap-server 会监听配置文件并热加载。上线后应检查服务日志，确认 Host checker 配置加载成功。

## 日常运营监控

建议至少监控以下指标：

- `x-probe` 服务存活率、异常退出和重启次数。
- `/attach/start`、`/attach/upload` 的请求量、延迟、429 和 5xx。
- agent 数据目录中 `spool/` 的文件数量和磁盘占用。
- `dead-letter/` 是否出现文件；正常情况下应为 0。
- `capability/degraded` 事件数量及受影响类别。
- Kafka `host_fact_audit` 的生产失败、消费延迟和磁盘占用。
- Redis 可用性、幂等命中率和内存使用。
- 进程运行快照、网络连接和业务文件事件的日均数据量。

默认 spool 上限为 10,000 条、100 MiB、最长保留 7 天。探针日志达到 10 MiB 时轮转一次。项目虽然改名为 `agent-host`，既有运行数据目录仍保留 `synap-host` 名称，以保持 GUID、会话、spool 和审计游标连续。

不要删除或跨主机复制数据目录。多台主机共用同一数据目录会导致 GUID、锁和审计游标冲突。

## 升级与回滚

推荐升级顺序：

1. 备份 synap-server 配置和当前 agent-host 二进制。
2. 先升级 synap-server，并验证 Kafka、Redis 和 Host checker 配置。
3. 选择一台主机灰度升级 `x-probe`。
4. 检查 spool、dead-letter、capability 事件和 Kafka 数据。
5. 完成三平台验收后再批量升级。

Linux 原地升级示例：

```bash
sudo systemctl stop synap-host
sudo install -m 0755 x-probe /usr/local/sbin/x-probe
sudo systemctl start synap-host
sudo systemctl status synap-host
```

升级和回滚时必须保留原数据目录。回滚时恢复旧二进制并重启服务，不要清空 spool 或 `audit-state`。

## 故障排查

### 一直没有数据上报

1. 查看 `logs/probe.log` 中的 start/upload 错误。
2. 检查 `--server` 是否为不带路径、查询参数和认证信息的 HTTP(S) origin。
3. 检查 synap-server `11099` 端口、Kafka 和 Redis。
4. spool 持续增长通常表示网络错误、429、服务端 5xx 或 Kafka 发布失败。
5. dead-letter 增长通常表示 HTTP 4xx、业务 `status=0` 或 AuditData 契约被服务端拒绝。

### 审计类别缺失

检查 `capability/degraded` 事件及以下系统能力：

- Linux：auditd、journald/auth 日志、`/proc`、`/sys` 和 udev 权限。
- Windows：Security、Sysmon、Task Scheduler Operational 和 Kernel-PnP Event Log。
- macOS：OpenBSM、eslogger、Unified Log、IOKit 和 Full Disk Access。

### 外网 IP 是 IPv6

默认公网 IP 服务允许返回 IPv4 或 IPv6，这两者都是合法结果。如果部署要求只上报 IPv4，可设置一个 IPv4-only 查询地址，例如：

```bash
--public-ip-url 'https://api.ipify.org?format=json'
```

### 传输安全

生产环境必须使用 HTTPS。Host 在 TLS 之上使用 Secure Communication v1，提供
服务端公钥固定、安装身份持有证明、AES-256-GCM 应用层加密、完整性和重放保护。
`code` 是密文内的业务 header，仍由服务端执行原有业务校验，但不单独作为密码学
身份凭据。SDK 不自动重试业务 POST：spool 重试保留原业务 `batchId/eventId`，每次
传输由安全客户端生成新的 `seq/requestId` 并重新加密。
