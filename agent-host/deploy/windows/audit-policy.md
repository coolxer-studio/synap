# Windows 审计策略部署

以下命令应由管理员通过组策略或受控部署流程执行，`agent-host` 本身不会执行这些命令，也不会安装 Sysmon。

```powershell
auditpol /set /subcategory:"Logon" /success:enable /failure:enable
auditpol /set /subcategory:"Logoff" /success:enable /failure:enable
auditpol /set /subcategory:"User Account Management" /success:enable /failure:enable
auditpol /set /subcategory:"Security Group Management" /success:enable /failure:enable
auditpol /set /subcategory:"Process Creation" /success:enable /failure:enable
auditpol /set /subcategory:"Process Termination" /success:enable /failure:enable
auditpol /set /subcategory:"File System" /success:enable /failure:enable
auditpol /set /subcategory:"Other Object Access Events" /success:enable /failure:enable
wevtutil sl Microsoft-Windows-TaskScheduler/Operational /e:true
```

能力检查：

```powershell
auditpol /get /category:*
Get-WinEvent -ListLog Security,Microsoft-Windows-Sysmon/Operational,Microsoft-Windows-TaskScheduler/Operational
Get-WinEvent -LogName Microsoft-Windows-Kernel-PnP/Configuration -MaxEvents 5
```

服务账号需能读取上述 Event Log，并拥有业务目录的只读权限。建议通过 Windows Service 运行，将 `--data-dir` 指向仅 SYSTEM/管理员可访问的目录。
