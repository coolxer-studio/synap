# macOS 审计部署

探针应以 root LaunchDaemon 运行。OpenBSM、Unified Log 与 Endpoint Security `eslogger` 由部署方预先启用和授权，探针不修改系统安全策略。

能力检查：

```sh
sudo audit -s
sudo praudit -l /var/audit/current | head
sudo eslogger exec fork exit open close rename unlink authentication login logout
/usr/bin/log show --last 1m --predicate 'process == "sshd" OR process == "opendirectoryd"'
/usr/sbin/ioreg -p IOUSB -l -w 0
```

在较新 macOS 上，应通过 MDM 为服务授予 Full Disk Access。缺少 OpenBSM/eslogger 权限时，探针仍使用进程、连接、文件、IOKit 和 launchd 快照差异，并上报 `capability/degraded`。
