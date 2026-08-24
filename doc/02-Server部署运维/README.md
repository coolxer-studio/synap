# Server 部署运维

`synap-server` 是数据接收和规则执行服务，负责下发 checker 配置、校验 Agent 上报、构建 Fact、执行 Groovy 规则并将 Checkpoint 写入 Kafka。

## 运行基线

| 项目 | 要求 |
| --- | --- |
| Java | 17 |
| 框架 | Spring Boot 3.2.0 |
| 默认端口 | `11099` |
| 消息系统 | Kafka |
| 幂等与会话 | 生产环境使用 Redis |
| 配置 | checker、punish、msg API、Groovy 规则文件 |

## 核心处理流程

```text
/sc/v1/**
  -> 安全会话、解密和重放校验
  -> 逻辑路由白名单
  -> AttachController / CrossController
  -> FactBuilder
  -> RuleEngineImpl
  -> Agenda / Punish
  -> Response + Kafka Checkpoint
```

## 部署导航

- 本地开发：运行 `mvn spring-boot:run`。
- 容器环境：[Docker Compose 部署](Docker-Compose部署.md)。
- 生产参数：[生产配置与安全通信](生产配置与安全通信.md)。
- 上线检查与问题处理：[验收、升级与排障](验收升级与排障.md)。

> Server 的 checker、规则和动作配置支持热加载，但环境变量和密钥仍应按发布流程管理。生产变更前先验证 JSON、规则兼容性和回滚材料。
