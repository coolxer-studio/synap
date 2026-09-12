# Server 部署

`synap-server` 是数据接收和规则执行服务，负责下发 checker 配置、校验 Agent 上报、构建 Fact、执行 Groovy 规则并将 Checkpoint 写入 Kafka。

## 1. 部署环境准备

- Linux 服务器、Docker Engine，以及 Docker Compose v2（推荐）或 legacy `docker-compose` 1.29.2。
- 可用的 Redis，用于幂等、sequence、风险状态和安全通信会话。
- 可用的 Kafka，分区数和副本数不能超过 Kafka 集群能力。
- Asset、Operation、Risk 和 Zenvis 等下游服务。
- 可拉取 synap-server 镜像的镜像仓库凭据。
- HTTPS 入口网关或负载均衡器。不要将明文 `11099` 直接暴露到公网。

建议只允许入口网关访问 `SYNAP_PORT`，Redis、Kafka 和下游服务使用内网连接。

## 2. 生成安全通信密钥

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

## 3. 准备生产环境变量

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
SYNAP_IMAGE_TAG=1.0.0.alpha
IMAGE_ARCH=amd64

ZENVIS_NETWORK_NAME=deploy_zenvis-network
KAFKA_BOOTSTRAP_SERVERS=kafka-service:9092
KAFKA_TOPIC_PARTITIONS=3
KAFKA_TOPIC_REPLICAS=3
KAFKA_PUBLISH_TIMEOUT=PT5S
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
SYNAP_APP_ORIGINS=h5-prod=https://app.example.com|https://admin.example.com
SYNAP_WECHAT_APPIDS=wechat-prod=wx1234567890abcdef|wxabcdef1234567890
SYNAP_REQUIRE_WECHAT_APPID=true

SYNAP_SC_ENABLED=true
SYNAP_SC_REQUIRE_TLS=true
SYNAP_SC_SERVER_KEY_ID=server-key-2026-01
SYNAP_SC_SERVER_PRIVATE_KEY=<PKCS#8 DER Base64URL>
SYNAP_SC_SERVER_PUBLIC_KEY=<SPKI DER Base64URL>
SYNAP_SC_SESSION_RECORD_KEY=<32 字节 Base64URL>
SYNAP_SC_ALLOWED_APP_IDS=synap-h5,synap-host,synap-android,synap-ios
SYNAP_SC_ENROLLMENT_ADMIN_TOKEN=<管理面长随机令牌>
```

### 代理、H5 与微信准入参数

| 参数 | 作用 | 格式与生产要求 |
| --- | --- | --- |
| `SYNAP_TRUSTED_PROXIES` | 指定可以提供客户端地址和 Synap 网关信号的直接代理 | 逗号分隔的 IPv4/IPv6 CIDR；只填写真实入口网关，服务直连时留空 |
| `SYNAP_CORS_ALLOWED_ORIGINS` | 配置 H5 跨域及安全握手的全局 Origin 白名单 | 逗号分隔的 Origin；生产使用实际 HTTPS Origin，启用 H5 时不能为空 |
| `SYNAP_APP_ORIGINS` | 按业务 `appKeyId` 覆盖 H5 Origin 白名单 | `appKeyId=Origin1|Origin2;另一个appKeyId=Origin1` |
| `SYNAP_WECHAT_APPIDS` | 按业务 `appKeyId` 配置微信小程序 AppID 白名单 | `appKeyId=wxAppId1|wxAppId2;另一个appKeyId=wxAppId3` |
| `SYNAP_REQUIRE_WECHAT_APPID` | 控制微信请求是否必须命中 AppID 白名单 | 生产建议为 `true`；为 `true` 且白名单为空时，微信请求会被拒绝 |

`SYNAP_TRUSTED_PROXIES` 判断的是与 Server 建立 TCP 连接的直接对端。只有直接对端命中该列表，Server 才会解析 `Forwarded`、`X-Forwarded-For`、`X-Real-IP` 以及 `X-Synap-Network-*` 等网关信号头；其他请求携带的同名头会被忽略。不得配置 `0.0.0.0/0`，否则客户端可能伪造来源地址和网关信号。例如同时信任 IPv4 和 IPv6 网关：

```dotenv
SYNAP_TRUSTED_PROXIES=10.20.0.0/16,2001:db8:100::/48
```

Origin 包含协议、主机名和非默认端口，但不包含路径。生产环境应优先配置精确的 HTTPS Origin，不要使用覆盖所有站点的通配符：

```dotenv
SYNAP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

`SYNAP_CORS_ALLOWED_ORIGINS` 同时用于 `/sc/v1/**` 的浏览器 CORS 和 H5 握手准入。`SYNAP_APP_ORIGINS` 则用于解密后的 H5 业务请求校验：Server 根据 `meta.appKeyId` 查找应用级 Origin；没有找到对应项时回退到全局 Origin 白名单。业务请求中的 `common.appPackage` 还必须等于 Origin 的主机名。例如：

```dotenv
SYNAP_APP_ORIGINS=h5-prod=https://app.example.com|https://admin.example.com;h5-test=http://localhost:8888
```

微信白名单同样根据 `meta.appKeyId` 查找，随后要求 `common.appPackage` 等于允许的微信小程序 AppID：

```dotenv
SYNAP_WECHAT_APPIDS=wechat-prod=wx1234567890abcdef|wxabcdef1234567890;wechat-test=wx1111111111111111
SYNAP_REQUIRE_WECHAT_APPID=true
```

当前版本的微信小程序尚未接入安全通信 v1；上述微信参数保留用于现有业务校验和后续迁移测试。安全通信开启后，微信小程序不能绕过安全入口直接访问受保护的明文业务路径。

### 安全通信 v1 参数

安全通信 v1 使用 `P-256 + HKDF-SHA256 + AES-256-GCM`。它为握手身份、业务密文、会话和重放窗口提供应用层保护，但不能替代 HTTPS。

| 参数 | 作用 | 保密要求与生产要求 |
| --- | --- | --- |
| `SYNAP_SC_ENABLED` | 同时启用安全通信端点、Server 身份、会话组件和明文业务路由保护 | 生产启用后必须完整配置本表中的密钥和允许列表 |
| `SYNAP_SC_REQUIRE_TLS` | 要求安全通信请求通过 TLS | 生产必须为 `true`；`false` 仅用于隔离的 HTTP 联调环境 |
| `SYNAP_SC_SERVER_KEY_ID` | 标识当前服务端身份公钥的版本 | 公开标识；轮换公钥时同步更新客户端固定配置和 key ID |
| `SYNAP_SC_SERVER_PRIVATE_KEY` | 服务端 P-256 身份私钥 | 机密；PKCS#8 DER 的 Base64URL 编码，不带 `=` 填充 |
| `SYNAP_SC_SERVER_PUBLIC_KEY` | 与服务端私钥配对、供客户端固定的公钥 | 可公开；SPKI DER 的 Base64URL 编码，不带 `=` 填充 |
| `SYNAP_SC_SESSION_RECORD_KEY` | 加密 Redis 中的安全会话记录 | 机密；32 字节随机值的 Base64URL 编码，所有 Server 实例必须相同 |
| `SYNAP_SC_ALLOWED_APP_IDS` | 允许进行安全握手的协议级 `appId` 白名单 | 逗号分隔的公开标识；只列出实际启用的客户端 |
| `SYNAP_SC_ENROLLMENT_ADMIN_TOKEN` | 保护原生端单次注册令牌的签发接口 | 高权限机密；使用足够长的随机值，并额外限制管理接口的网络访问 |

启用 `SYNAP_SC_ENABLED` 后，外部数据入口为 `/sc/v1/handshake`、`/sc/v1/handshake/finish` 和 `/sc/v1/message`。`/attach/**`、`/cross/**`、`/risk/**` 等业务路径必须由安全消息解密后转发，直接明文访问会返回 404。

`SYNAP_SC_REQUIRE_TLS=false` 只允许在受控开发网络中临时使用。即使应用正文已经加密，HTTP 仍缺少证书验证和标准传输层保护。生产通过网关终止 TLS 时，应正确传递并识别外部 HTTPS 协议。

服务端身份密钥必须跨重启保持稳定。轮换 `SYNAP_SC_SERVER_PRIVATE_KEY` 和 `SYNAP_SC_SERVER_PUBLIC_KEY` 时，必须同步更新客户端固定的服务端公钥及 `SYNAP_SC_SERVER_KEY_ID`。轮换 `SYNAP_SC_SESSION_RECORD_KEY` 会使 Redis 中已有的安全会话失效；同一集群内使用不同会话记录密钥也会造成请求随机失败。

`SYNAP_SC_ALLOWED_APP_IDS` 中的值是安全握手使用的公开协议 `appId`，不是业务 `meta.appKeyId`，也不是 H5 域名或微信 AppID。例如：

```dotenv
SYNAP_SC_ALLOWED_APP_IDS=synap-h5,synap-host,synap-android,synap-ios
```

原生端、Host 和 Server Agent 首次安装所需的短时单次注册令牌，由以下管理接口签发：

```http
POST /admin/sc/v1/enrollment-tokens
X-Synap-Admin-Token: <SYNAP_SC_ENROLLMENT_ADMIN_TOKEN>
```

管理令牌不能内置到普通 Agent 或 H5 代码中。接口还应由内网、管理网关或访问控制策略限制；签发出的注册令牌有效期必须在 30～3600 秒之间。

密钥文件和环境变量的管理要求：

- 私钥、会话记录密钥和管理令牌不得提交到 Git、写入镜像、输出到日志或发送到聊天系统。
- 所有 synap-server 实例应使用相同的服务端密钥对和会话记录密钥。
- 密钥应由 Secret Manager、Kubernetes Secret 或权限为 `0600` 的部署环境文件注入。
- `SYNAP_SC_SERVER_KEY_ID`、服务端公钥、`SYNAP_SC_ALLOWED_APP_IDS` 可以公开，但仍应纳入版本管理和变更审计。

这些配置涉及三类不同的标识，配置时不要混用：

| 标识 | 所在位置 | 用途 |
| --- | --- | --- |
| `meta.appKeyId` | 解密后的业务元数据 | 查找 `SYNAP_APP_ORIGINS` 或 `SYNAP_WECHAT_APPIDS` |
| `common.appPackage` | 业务公共字段 | H5 中表示宿主域名，微信中表示微信小程序 AppID |
| 安全通信 `appId` | 握手消息 | 由 `SYNAP_SC_ALLOWED_APP_IDS` 控制是否允许握手 |
| `SYNAP_SC_SERVER_KEY_ID` | 服务端握手身份 | 标识客户端当前应信任的服务端公钥版本 |

## 4. 准备 Docker 网络

Compose 使用外部网络 `ZENVIS_NETWORK_NAME`。先确认该网络已由 Zenvis 或基础设施部署创建：

```bash
docker network inspect deploy_zenvis-network
```

如果尚未创建且确定应使用该名称，可执行：

```bash
docker network create deploy_zenvis-network
```

Kafka、Redis 和其他使用服务名访问的下游容器必须加入同一网络。

## 5. 部署前检查

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

## 6. 启动和验证

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

## 7. 升级和回滚

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

## 8. 停止服务

```bash
docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  down
```

Compose 中的 Zenvis 网络是外部网络，`down` 不会删除它。Redis 和 Kafka 数据也不在本 Compose 项目内，其备份和恢复应由对应基础设施流程负责。

## 9. 验收、升级与排障

### 上线验收清单

- [ ] 记录 Server 镜像、Git commit、规则版本、checker 版本和安全 SDK 版本。
- [ ] TLS、证书链、Server 公钥信任锚和允许 appId 与 Agent 配置一致。
- [ ] Kafka topic、分区、副本和发布超时符合环境要求。
- [ ] Redis 可用，幂等、会话和注册令牌命名空间相互隔离。
- [ ] CORS、H5 Origin、可信代理 CIDR 和请求限制已显式配置。
- [ ] 明文 `/attach/*`、`/cross/*` 与旧安全路径不可从外部访问。
- [ ] start、upload、ACK、重复 messageId、sequence 异常和 Kafka Checkpoint 已验证。
- [ ] 日志、指标、告警、备份和回滚责任人明确。

### 健康判断

Compose 的 TCP 健康检查只能说明进程端口可连接。完整健康判断还应包含：

1. 配置加载成功且没有持续热加载错误。
2. Kafka 发布成功，无超时或持续重试。
3. Redis 连接和幂等读写正常。
4. 测试 Agent 可完成安全握手和业务 ACK。
5. 下游可消费目标 Checkpoint topic。

### 升级步骤

1. 在预发布环境使用生产同构配置验证新镜像、规则与 Agent 组合。
2. 备份配置文件、密钥引用、镜像摘要和回滚命令；不导出明文 Secret 到普通工单。
3. 先保持规则为观察模式，避免新信号直接触发高风险动作。
4. 小流量发布 Server，观察握手失败率、ACK、幂等、Kafka 和 Redis。
5. 再分批升级 Agent，并保留旧版本回退窗口。

密钥轮换是独立变更：先让 Agent 信任新 key ID，再切换 Server 身份，最后移除旧信任锚。不要把密钥轮换与大版本升级合并为不可回退的一步。

### 常见问题

#### 握手失败

检查 HTTPS 地址、证书、key ID、SPKI 信任锚、允许 appId、Server 时钟和一次性注册令牌。H5 还需检查当前页面 Origin。

#### upload 重复或丢失

Agent 业务重试必须保留 `messageId/batchId/sequence`，同时让传输层生成新的 `seq/requestId`。检查 Redis 幂等记录、ACK 结果和 dead-letter，而不是手工修改业务标识。

#### Fact 未进入 Kafka

依次检查平台与 Fact 命名、checker 字段顺序、字段白名单、规则异常、Kafka topic 和发布超时。未知或不匹配的 Fact 可能在构造阶段被跳过。

#### 来源 IP 不正确

确认负载均衡器的直接地址属于 `SYNAP_TRUSTED_PROXIES`，并从代理链右向左剥离可信节点。不得为了快速修复而信任所有地址。

#### 配置变更未生效

检查容器内只读挂载路径、文件修改时间、JSON/Groovy 语法和热加载日志。环境变量只在进程启动时读取，需要重启服务。

---

> Server 的 checker、规则和动作配置支持热加载，但环境变量和密钥仍应按发布流程管理。生产变更前先验证 JSON、规则兼容性和回滚材料。
