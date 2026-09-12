# Host 接入与运维

Host Agent 支持 Linux、Windows 和 macOS，采集主机资产并持续接收系统审计事件。它只读取预先部署的审计设施；权限或组件缺失时上报 `capability/degraded`，不会自动修改审计策略或伪造事件。

## 构建与测试

在 `synap-agent/agent-host` 中按项目 Makefile 构建。发布流程应固定版本、目标系统和架构，并验证 `x-probe` 或 `x-probe.exe` 的完整性。

常用运行参数和环境变量包括：

| 参数 / 环境变量 | 用途 |
| --- | --- |
| `--server` / `SYNAP_HOST_SERVER` | Server 地址 |
| `--public-ip-url` / `SYNAP_HOST_PUBLIC_IP_URL` | 公网 IP 查询地址 |
| `--app-id` / `SYNAP_HOST_APP_ID` | 业务数字 appId |
| `SYNAP_SC_APP_ID` | 安全协议 appId，与业务数字 appId 不同 |
| `SYNAP_SC_SERVER_KEY_ID` | Server key ID |
| `SYNAP_SC_TRUST_ANCHOR` | P-256 SPKI Base64URL 信任锚 |
| `SYNAP_SC_ENROLLMENT_TOKEN` | 首次安装所需的短时单次注册令牌 |

当前实现接受 HTTP 或 HTTPS Origin，且不会把 HTTP 限制在 loopback。生产环境仍建议使用
HTTPS；应用层协议 v1 不替代 TLS 的证书验证和网络层保护。Server 默认仍要求 TLS，HTTP
联调还需要显式的非生产 Server 配置。

## 首次安装

1. 通过管理接口为目标设备类型和协议 appId 签发单次注册令牌。
2. 由受控部署系统注入令牌、Server 地址、key ID 和信任锚。
3. 确保持久身份与 spool 目录只允许运行账号访问。
4. 首次注册成功后移除部署环境中的注册令牌，不删除或复制已生成身份文件。
5. 验证 start、采集、upload、ACK 和 Server 侧 Host Checkpoint。

克隆身份文件会导致设备身份冲突。旧身份格式不会自动迁移时，升级后的首次启动需要新的单次令牌。

## 系统审计准备

- Linux：由运维流程部署 auditd 规则和服务模板。
- Windows：预先启用 Security、Task Scheduler、PnP 日志；如使用 Sysmon，通过组织策略部署配置。
- macOS：按项目 `deploy/macos` 材料准备权限和服务配置。

Agent 本身不会安装 Sysmon、执行 `auditpol` 或修改 auditd 策略。

## 业务目录和审计策略

Host 的 `auditPolicy.businessDirectories` 默认值为空。部署方必须在 Server 的 `checker_config/host.json` 中列出需要监控的核心业务目录；不要直接使用过宽的系统根目录。

## 审计数据（AuditData）

Host upload 新增可选的第六类 `AuditData`，旧端点、旧 `EventData.sshd`、数字查询别名和原有五类 Fact 不受影响。协议仍是 `code` Header、24 项 `common` 和数组型 Fact。

事件固定 18 列：

`eventId,eventTime,category,action,result,actor,subject,target,pid,process,localEndpoint,remoteEndpoint,protocol,digestAlgorithm,digest,source,bootId,detail`

服务端限制为每批 1–100 条、事件数组 128 KiB、单条详情 4 KiB，并严格校验 UUID `batchId`、RFC 3339 时间、类别、动作和结果枚举。合法批次先按 `guid:batchId` 认领幂等键，随后执行风险规则并发布 `host_fact_audit`。Kafka key 为 `<guid>:<batchId>`，topic 使用 `delete` 策略并保留 180 天。发布失败会释放幂等键并返回 5xx；已完成的重复批次返回 `status=1`。

所有合法事件都会发布 Kafka，是否命中风险规则不会影响保留。当前提醒只覆盖连续登录失败、账户权限变更、关键配置变更、未知 USB、可疑计划任务和审计能力降级。查询 API 和审计页面不在本服务当前范围内。

默认采集策略位于 `deploy/open_config/checker_config/host.json` 的 `auditPolicy`。业务目录默认为空，必须由部署方按实际核心业务路径配置。端点部署模板位于 `synap-agent/agent-host/deploy`。

## 日常运维

- 监控进程存活、capability/degraded、队列深度、最老待发送记录和 ACK 延迟。
- 检查主机审计日志是否持续产生，轮转后读取位置能否恢复。
- 公网 IP 服务应允许 IPv4/IPv6，且 Server 仍独立记录真实请求来源 `requestIp`。
- 升级先小批量验证，保留旧二进制、服务配置和身份目录；回滚不得覆盖身份文件。
