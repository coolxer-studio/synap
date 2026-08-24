# Synap 安全探针平台

> **产品定位：连接多端安全探针、加密通信、风险规则与工程化接入的统一平台。**
>
> Synap 由 `synap-server`、`synap-agent` 和 `synap-build` 三部分组成。本手册面向接入与运维人员，集中说明如何部署服务、接入探针、验证数据链路和处理常见故障。

![Synap 产品横幅](_images/synap-banner.png)

## 一条主链路理解 Synap

```text
业务应用 / 浏览器 / 桌面 / 移动端 / 主机 / 嵌入式设备
  -> Synap Agent 采集并形成 Fact
  -> 安全通信协议 v1 建立会话并加密传输
  -> Synap Server 校验、幂等、构建 Fact
  -> Groovy 规则生成 Agenda / Punish 建议
  -> Kafka Checkpoint 进入后续分析与处置
```

Synap Build 是这条链路的工程化入口：它根据模板和安全参数修改目标仓库，输出修改后工程、Git patch 与任务报告，但不会代替使用方构建、测试、提交或推送目标工程。

## 三大组成部分

| 组件 | 负责什么 | 常见使用者 |
| --- | --- | --- |
| Synap Server | 接收 Agent 数据、下发采集配置、执行规则并写入 Kafka | 平台运维、规则开发者 |
| Synap Agent | H5、Electron、Host、Qt、Spring Boot、Android、iOS 探针，以及 HarmonyOS、ESP32 待适配客户端和仿真工具 | 应用接入方、终端运维 |
| Synap Build | 用模板驱动 AI 完成探针 SDK 工程修改并生成交付包 | 接入工程师、平台管理员 |

## 从哪里开始

- 第一次搭建环境：阅读[快速开始](/01-产品与快速开始/快速开始.md)。
- 准备生产服务：阅读[Server 部署运维](/02-Server部署运维/README.md)。
- 选择并接入探针：阅读[Agent 接入](/03-Agent接入/README.md)。
- 使用自动化工程接入：阅读[Synap Build](/04-Synap-Build/README.md)。
- 联调协议或接口：阅读[协议与 API](/05-协议与API/README.md)。

> 生产 Agent 只访问 `/sc/v1/**` 安全通信入口。`/attach/*` 与 `/cross/*` 是密文内的逻辑路径，不应通过公网明文直接调用。
