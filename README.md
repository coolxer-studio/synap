# Synap

![Synap](doc/_images/synap-banner.png)

Synap 是一个以配置驱动的端点执行器，用于资产梳理、数字化运营、安全风险管控等场景下的数据采集与动态策略执行。业务团队通过 JSON 结构化配置定义采集字段、窗口和策略，配合多平台 Agent 完成端点接入、安全通信、数据上报和规则执行，无需改代码即可扩展端点和调整采集范围。

## 项目组成

| 目录 | 作用 | 技术栈 |
| --- | --- | --- |
| `synap-server` | 数据接收和规则执行服务，下发 Checker 配置、校验 Agent 上报、构建 Fact、执行规则并将 Checkpoint 写入 Kafka | Java 17、Spring Boot 3.2.0、Redis、Kafka |
| `synap-agent` | 多平台端点 SDK 与数据仿真工具集，覆盖 H5、Electron、Host、Qt、Spring Boot、Android、iOS 等平台 | TypeScript、C++、Kotlin/Swift、Go |
| `synap-build` | 模板驱动的 AI 工程修改工作台，在 CI/CD 环节将 Agent SDK 自动接入目标工程 | Go 1.23 |
| `synap-sdk` | Android 与 iOS 端点功能 SDK 模块集合 | Kotlin、Swift |
| `doc` | 产品、部署、接入、协议、架构与开发文档中心 | Markdown、Docsify 4 |

各子项目以自身 `README.md` 为构建与维护入口；跨模块的产品、架构、部署、使用与开发说明统一维护在 [Synap 文档中心](doc/README.md)。

## 快速开始

### Docker Compose 部署

`synap-server` 使用 Docker Compose 部署，依赖外部 Redis、Kafka 和下游服务。完整部署流程见 [Server 部署运维](doc/01-Server部署/README.md)。

准备环境变量文件后，从 `synap-server` 目录执行：

```bash
docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  pull

docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  up -d
```

默认服务入口：

| 服务 | 地址 |
| --- | --- |
| Synap Server | `http://localhost:11099` |
| Synap Build | `http://localhost:11100` |

安全通信 v1 启用后，外部数据入口为 `/sc/v1/handshake`、`/sc/v1/handshake/finish` 和 `/sc/v1/message`；明文业务路径不可直接访问。首次部署需生成 P-256 密钥对并配置 `SYNAP_SC_*` 系列环境变量，详细步骤见 [Server 部署运维](doc/01-Server部署/README.md)。

### 本地开发

Server：

```bash
cd synap-server
JAVA_HOME=/path/to/jdk-17 mvn clean package
mvn spring-boot:run
```

Build：

```bash
cd synap-build
export SYNAP_BUILD_TOKEN='replace-with-a-long-random-token'
export SYNAP_BUILD_ALLOWED_REPOS='gitee.com/coolxer-studio,github.com/your-org'
go run ./cmd/synap-build
```

Agent：各平台 SDK 的构建方式不同，详见 [Agent 接入](doc/02-Agent接入/README.md) 和对应平台文档。

## 核心能力

- 通过 Checker 配置定义各平台采集字段、窗口和策略，无需改代码即可扩展端点。
- 通过安全通信协议 v1（`P-256` + `HKDF-SHA256` + `AES-256-GCM`）实现端到端加密，Agent 固定 Server 信任锚。
- 通过 Fact 规则引擎将结构化事实转换为 Agenda 议题和 Punish 动作建议。
- 通过多端点 SDK 覆盖 H5、Electron、Host、Qt、Spring Boot、Android、iOS 等平台。
- 通过 Synap Build 在 CI/CD 环节以模板驱动 AI 完成 Agent SDK 自动接入，输出可审查的交付包。
- 通过 ReAct 自迭代循环对接 Zenvis，实现采集—分析—决策—执行的持续自适应闭环。

## 文档

| 主题 | 文档 |
| --- | --- |
| Server 部署 | [Server 部署](doc/01-Server部署/README.md) |
| Agent 接入 | [Agent 接入](doc/02-Agent接入/README.md) |
| Synap Build 接入 | [Synap Build 接入](doc/03-Synap-Build接入/README.md) |
| 协议与 API | [协议与 API](doc/04-协议与API/README.md) |
| 架构设计 | [架构与安全](doc/05-架构设计/README.md) |
| 开发指南 | [开发指南](doc/06-开发指南/README.md) |

## 许可证与联系

项目采用 [Apache License 2.0](LICENSE)。问题与建议可通过项目 Issue 或 <coolxer@163.com> 反馈。
