# Host AuditData 审计流

Host upload 新增可选的第六类 `AuditData`，旧探针、旧 `EventData.sshd`、数字查询别名和原有五类 Fact 不受影响。协议仍是 `code` Header、24 项 `common` 和数组型 Fact。

事件固定 18 列：

`eventId,eventTime,category,action,result,actor,subject,target,pid,process,localEndpoint,remoteEndpoint,protocol,digestAlgorithm,digest,source,bootId,detail`

服务端限制为每批 1–100 条、事件数组 128 KiB、单条详情 4 KiB，并严格校验 UUID `batchId`、RFC 3339 时间、类别、动作和结果枚举。合法批次先按 `guid:batchId` 认领幂等键，随后执行风险规则并发布 `host_fact_audit`。Kafka key 为 `<guid>:<batchId>`，topic 使用 `delete` 策略并保留 180 天。发布失败会释放幂等键并返回 5xx；已完成的重复批次返回 `status=1`。

所有合法事件都会发布 Kafka，是否命中风险规则不会影响保留。当前提醒只覆盖连续登录失败、账户权限变更、关键配置变更、未知 USB、可疑计划任务和审计能力降级。查询 API 和审计页面不在本服务当前范围内。

默认采集策略位于 `deploy/open_config/checker_config/host.json` 的 `auditPolicy`。业务目录默认为空，必须由部署方按实际核心业务路径配置。探针部署模板位于 `synap-agent/agent-host/deploy`。
