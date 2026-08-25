# Docker Compose 部署

仓库提供 `synap-server/deploy/docker-compose.yml`。该编排只定义 Synap Server，并加入既有的外部网络；Kafka、Redis 和相关业务服务需要由部署环境提供。

推荐使用 Docker Compose v2（`docker compose`）。同时兼容 legacy `docker-compose` 1.29.2，但不支持更低版本的 Compose v1。下文示例使用 v2；在 1.29.2 环境中将 `docker compose` 替换为 `docker-compose`，其余参数不变。项目名由 `COMPOSE_PROJECT_NAME` 设置，Compose 文件为兼容 v1 不使用顶层 `name:`。

## 前置条件

- 已准备与目标架构匹配的 Synap Server 镜像。
- 已安装 Docker Compose v2，或 legacy `docker-compose` 1.29.2。
- 已创建 Compose 所引用的外部网络。
- Kafka、Redis 和业务服务地址在该网络中可达。
- `deploy/open_config/` 下的 checker、rule、punish 和 msg API 配置已完成审核。
- 生产密钥通过 Secret 或等价机制注入，不写入 Compose 文件和镜像。

## 关键变量

| 变量 | 说明 |
| --- | --- |
| `SYNAP_IMAGE` | 镜像仓库与名称 |
| `SYNAP_IMAGE_TAG` | 镜像标签，默认 `latest` |
| `IMAGE_ARCH` | 镜像架构，默认 `amd64` |
| `SYNAP_PORT` | 宿主机映射端口，默认 `11099` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap 地址，必需 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 连接信息 |
| `ZENVIS_NETWORK_NAME` | 已存在的外部 Docker 网络名 |
| `ASSET_SERVICE_URL` / `OPERATION_SERVICE_URL` / `RISK_SERVICE_URL` | `/cross/*` 依赖的业务服务地址 |

## 启动

```bash
cd synap-server/deploy
docker compose config
docker compose up -d
docker compose ps
docker compose logs -f synap-server
```

`docker compose config` 会展开变量，执行后要确认输出中没有意外空值。不要把包含 Secret 的展开结果保存到日志或提交到 Git。

## 挂载与运行策略

Compose 将以下内容只读挂载到容器：

```text
open_config/identify_config  -> /app/config/identify_config
open_config/checker_config   -> /app/config/checker_config
open_config/punish_config    -> /app/config/punish_config
open_config/msg_api_config   -> /app/config/msg_api_config
```

服务使用 `restart: unless-stopped`、`no-new-privileges`、30 秒停止宽限期和轮转的 `json-file` 日志。健康检查仅确认容器内 `11099` 端口可连接，不代表 Kafka、Redis、配置和完整业务链路均已就绪。

## 容器上线检查

1. 容器状态健康，重启次数没有持续增加。
2. 配置目录挂载为只读，文件内容与发布清单一致。
3. Server 能连接 Kafka 和 Redis。
4. 外部只开放 HTTPS 反向代理后的 `/sc/v1/**`。
5. 使用测试 Agent 完成握手、start、upload、ACK 和 Kafka Checkpoint 验证。
