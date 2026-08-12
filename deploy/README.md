# synap-server 生产环境部署

本目录使用 Docker Compose 部署 `synap-server`。容器内部端口固定为 `11099`，对外端口由 `SYNAP_PORT` 配置。

生产环境不应把 Redis、Kafka、服务端私钥或管理令牌写入镜像、Git 仓库或 Compose 文件。本仓库的 `.env` 只是配置模板，真实密钥应由密钥管理系统或服务器上的受限文件注入。

## 1. 部署架构

生产环境需要事先准备：

- Linux 服务器、Docker Engine 和 Docker Compose V2。
- 可用的 Redis，用于幂等、sequence、风险状态和安全通信会话。
- 可用的 Kafka，分区数和副本数不能超过 Kafka 集群能力。
- Asset、Operation、Risk 和 Zenvis 等下游服务。
- 可拉取 synap-server 镜像的镜像仓库凭据。
- HTTPS 入口网关或负载均衡器。不要将明文 `11099` 直接暴露到公网。

建议只允许入口网关访问 `SYNAP_PORT`，Redis、Kafka 和下游服务使用内网连接。

## 2. 构建并推送镜像

构建机需要 JDK 17、Maven 和 Docker。项目使用 Groovy 3.0.17，不要使用 Java 26 执行测试。

在 `synap-server` 根目录执行：

```bash
java -version

IMAGE_ARCH=amd64 \
IMAGE_TAG=20260812.1 \
SKIP_TESTS=false \
PUSH_IMAGE=true \
./build.sh
```

ARM64 服务器将 `IMAGE_ARCH` 改为 `arm64`。构建脚本会生成两个带架构后缀的标签：

```text
<IMAGE_NAME>:20260812.1-amd64
<IMAGE_NAME>:<YYYYMMDD>-amd64
```

生产部署应使用不可变版本号，不要仅依赖 `latest`。如需使用其他镜像仓库，可同时指定 `IMAGE_NAME` 和 `BASE_IMAGE_REPOSITORY`。

## 3. 准备动态配置

Compose 会将 `open_config` 下的配置以只读方式挂载到容器。制作发布包时必须检查：

```text
deploy/open_config/
├── identify_config/    # Groovy 规则
├── checker_config/     # Agent checker 配置
├── punish_config/      # 处置动作配置
└── msg_api_config/     # 外部 API 配置
```

`identify_config` 不能为空。从源码目录制作发布包时，同步当前版本规则：

```bash
cp src/main/java/com/coolxer/rule/*.groovy deploy/open_config/identify_config/
ls -l deploy/open_config/identify_config/
```

规则和 checker/punish 配置必须与应用镜像版本匹配。升级前应将它们与 Compose 文件一起备份。

## 4. 生成安全通信密钥

仅当 `SYNAP_SC_ENABLED=true` 时需要本节密钥。密钥应在首次生产部署前生成一次，不应在每次构建或容器启动时重新生成。

在受限目录中生成 P-256 服务端密钥对：

```bash
umask 077
openssl genpkey \
  -algorithm EC \
  -pkeyopt ec_paramgen_curve:P-256 \
  -out server-private.pem

openssl pkey \
  -in server-private.pem \
  -pubout \
  -out server-public.pem
```

将私钥转换为 PKCS#8 DER Base64URL：

```bash
openssl pkcs8 -topk8 -nocrypt \
  -in server-private.pem \
  -outform DER |
openssl base64 -A |
tr '+/' '-_' |
tr -d '='
```

将公钥转换为 SPKI DER Base64URL：

```bash
openssl pkey -pubin \
  -in server-public.pem \
  -outform DER |
openssl base64 -A |
tr '+/' '-_' |
tr -d '='
```

执行两次分别生成 32 字节会话记录密钥和管理令牌：

```bash
openssl rand 32 |
openssl base64 -A |
tr '+/' '-_' |
tr -d '='

openssl rand -hex 32
```

四段输出依次用于：

1. `SYNAP_SC_SERVER_PRIVATE_KEY`
2. `SYNAP_SC_SERVER_PUBLIC_KEY`
3. `SYNAP_SC_SESSION_RECORD_KEY`
4. `SYNAP_SC_ENROLLMENT_ADMIN_TOKEN`

密钥管理注意事项：

- 所有 synap-server 实例必须使用相同的服务端密钥和会话记录密钥。
- 更换服务端公钥时，需要同步更新客户端的公钥固定配置和 `SYNAP_SC_SERVER_KEY_ID`。
- 更换会话记录密钥会使 Redis 中的已有安全会话失效。
- `SYNAP_SC_ALLOWED_APP_IDS` 是允许握手的公开 appId 列表，不是密钥。

## 5. 准备生产环境变量

不要直接在仓库的 `.env` 中保存真实密钥。可在部署服务器上创建独立文件：
本节及后续 Compose 命令均从 `synap-server` 根目录执行。

```bash
sudo install -d -m 700 /etc/synap
sudo install -m 600 deploy/.env /etc/synap/synap-server.env
sudoedit /etc/synap/synap-server.env
```

至少需核对以下配置：

```dotenv
COMPOSE_PROJECT_NAME=synap
TZ=Asia/Shanghai
SYNAP_PORT=11099

SYNAP_IMAGE=crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com/coolxer-studio/synap-server
SYNAP_IMAGE_TAG=20260812.1
IMAGE_ARCH=amd64

ZENVIS_NETWORK_NAME=deploy_zenvis-network
KAFKA_BOOTSTRAP_SERVERS=kafka-service:9092
KAFKA_TOPIC_PARTITIONS=3
KAFKA_TOPIC_REPLICAS=3
REDIS_HOST=redis-service
REDIS_PORT=6379
REDIS_PASSWORD=<生产 Redis 密码>

ASSET_SERVICE_URL=http://asset-service:11003
OPERATION_SERVICE_URL=http://operation-service:11004
RISK_SERVICE_URL=http://risk-service:11005
ZENVIS_BUSINESS_SERVICE_ENABLED=true
ZENVIS_BASE_URL=http://zenvis-backend:11001

SYNAP_TRUSTED_PROXIES=10.20.0.0/16
SYNAP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com

SYNAP_SC_ENABLED=true
SYNAP_SC_REQUIRE_TLS=true
SYNAP_SC_SERVER_KEY_ID=server-key-2026-01
SYNAP_SC_SERVER_PRIVATE_KEY=<PKCS#8 DER Base64URL>
SYNAP_SC_SERVER_PUBLIC_KEY=<SPKI DER Base64URL>
SYNAP_SC_SESSION_RECORD_KEY=<32 字节 Base64URL>
SYNAP_SC_ALLOWED_APP_IDS=synap-h5,synap-host,synap-android,synap-ios
SYNAP_SC_ENROLLMENT_ADMIN_TOKEN=<管理面长随机令牌>
```

`SYNAP_TRUSTED_PROXIES` 必须仅包含真实入口网关的 CIDR，不得设置为 `0.0.0.0/0`。`SYNAP_CORS_ALLOWED_ORIGINS` 必须列出所有允许的浏览器 Origin，生产启用 H5 时不能为空。

## 6. 准备 Docker 网络

Compose 使用外部网络 `ZENVIS_NETWORK_NAME`。先确认该网络已由 Zenvis 或基础设施部署创建：

```bash
docker network inspect deploy_zenvis-network
```

如果尚未创建且确定应使用该名称，可执行：

```bash
docker network create deploy_zenvis-network
```

Kafka、Redis 和其他使用服务名访问的下游容器必须加入同一网络。

## 7. 部署前检查

登录镜像仓库：

```bash
docker login crpi-4pdi7kz96g4v0tg3.cn-beijing.personal.cr.aliyuncs.com
```

展开并校验 Compose 配置：

```bash
docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  config --quiet
```

`docker compose config` 的完整输出会包含已展开的密钥，生产环境不要将完整输出写入日志或发送给他人。

部署前还应确认：

- `IMAGE_ARCH` 与服务器及镜像标签一致。
- `SYNAP_IMAGE_TAG` 对应的 `<tag>-<arch>` 镜像已推送。
- `identify_config` 中存在当前版本的 Groovy 规则。
- Redis、Kafka、下游服务和外部 Docker 网络可用。
- HTTPS 入口、证书、Origin 和可信代理 CIDR 配置正确。
- 安全通信启用时，所有密钥和 appId 均不为空。

## 8. 启动和验证

拉取镜像并启动：

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

检查容器和健康状态：

```bash
docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  ps

docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  logs --tail=200 synap-server
```

容器应在启动宽限期后显示为 `healthy`。还需通过 HTTPS 入口执行一次真实的客户端握手和业务消息验收，并确认：

- Redis 中可正常创建会话、重放保护和幂等记录。
- Kafka 对应 topic 可正常写入。
- checker、punish、msg API 和 Groovy 规则已加载。
- Asset、Operation、Risk 和 Zenvis 调用正常。

## 9. 升级和回滚

升级前备份当前环境变量和 `open_config`，然后修改 `SYNAP_IMAGE_TAG`、拉取镜像并重建服务：

```bash
docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  pull synap-server

docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  up -d --no-deps synap-server
```

回滚时恢复上一版 `SYNAP_IMAGE_TAG` 和与其匹配的 `open_config`，再执行相同的 `pull` 和 `up -d --no-deps`。

不要在普通升级中重新生成安全通信密钥。如果必须轮换密钥，应单独制定客户端公钥更新、已有会话失效和回滚方案。

## 10. 停止服务

```bash
docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  down
```

Compose 中的 Zenvis 网络是外部网络，`down` 不会删除它。Redis 和 Kafka 数据也不在本 Compose 项目内，其备份和恢复应由对应基础设施流程负责。
