# Synap Server

Synap Server 是 Synap 安全探针平台的数据接入与规则执行服务。它负责接收 Agent 数据、下发采集配置、构造 Fact、执行 Groovy 规则，并将 Checkpoint 写入 Kafka。生产部署只应公开由安全通信协议 v1 保护的数据入口。

## 阅读导航

- 首次运行：参阅[本地开发](#本地开发)。
- 生产上线：参阅[生产部署](#生产部署)。
- 增加采集模型或规则：参阅[扩展 Fact 与规则](#扩展-fact-与规则)。
- 完整接口与接入文档统一维护在 Synap 主仓库：
  - [系统与数据链路](https://gitee.com/coolxer-studio/synap/blob/master/doc/06-架构与安全/系统与数据链路.md)
  - [Server API 完整参考](https://gitee.com/coolxer-studio/synap/blob/master/doc/05-协议与API/Server-API-完整参考.md)
  - [服务端应用安全探针协议](https://gitee.com/coolxer-studio/synap/blob/master/doc/05-协议与API/服务端应用安全探针协议.md)
  - [H5 检测规则与验收说明](https://gitee.com/coolxer-studio/synap/blob/master/doc/03-Agent接入/H5检测规则.md)
  - [微信小程序检测与上报规则](https://gitee.com/coolxer-studio/synap/blob/master/doc/03-Agent接入/微信小程序检测规则.md)
  - [Host AuditData 审计流](https://gitee.com/coolxer-studio/synap/blob/master/doc/03-Agent接入/Host审计数据.md)

## 核心能力

- `start`：Agent 启动时获取当前平台的 checker 配置并执行启动规则。
- `upload`：接收批量采集数据，构造 Fact、执行规则并生成 Agenda、Punish 和风险结果。
- `cross`：按动态配置代理 Asset、Operation、Risk 等下游查询。
- 可靠处理：提供 messageId 幂等、sequence 观察、风险聚合和 Kafka Checkpoint 投递。
- 配置热加载：运行时重新加载 checker、punish、msg API 和 Groovy 规则。

## 技术栈

| 领域 | 组件 |
| --- | --- |
| 运行时 | Java 17、Spring Boot 3.2.0、Spring Web |
| 规则与序列化 | Groovy 3.0.17、Jackson |
| 消息与状态 | Spring Kafka、Spring Data Redis |
| 工程辅助 | Maven、Lombok、Docker |

## 目录结构

```text
src/main/java/com/coolxer
├── Application.java              # 启动类
├── config                        # Kafka topic、跨域、配置监听、JSON 工具配置
├── controller                    # HTTP 接口入口
├── integration                   # Zenvis 等外部集成
├── model                         # VO、Fact、Agenda、Punish、Checkpoint 等模型
├── rule                          # Groovy 规则脚本
├── service                       # 幂等、sequence、session 与业务服务
├── service/impl                  # 规则执行、动态配置和跨服务调用实现
└── utils                         # Jackson、反射、字符串转换工具

src/main/resources
├── application.properties        # 公共配置与默认 dev profile
├── application-dev.properties    # 本地开发配置
└── application-prod.properties   # 生产配置

deploy
├── docker-compose.yml            # 生产容器编排
├── .env                          # 环境变量模板
└── open_config
    ├── checker_config            # 各平台 checker 配置
    ├── identify_config           # 生产规则脚本
    ├── punish_config             # Agenda -> Punish 映射
    └── msg_api_config            # /cross/{id} 外部 API 配置
```

## 核心流程

```text
Agent / Server SDK
  -> AttachController
  -> 平台结构校验（H5 额外校验 Origin / Fetch Metadata，WeChat 校验 appId）
  -> 安全信封解密 + 逻辑请求校验
  -> sequence 缺口 / 倒序观察
  -> messageId 幂等检查
  -> FactBuilder / StartFact
  -> RuleEngineImpl
  -> Groovy Rule
  -> Agenda
  -> PunishConfig
  -> ResponseVO + Kafka Checkpoint
```

核心代码入口：

- 启动类：`src/main/java/com/coolxer/Application.java`
- 数据接口：`src/main/java/com/coolxer/controller/AttachController.java`
- 跨服务接口：`src/main/java/com/coolxer/controller/CrossController.java`
- Fact 构造：`src/main/java/com/coolxer/model/fact/FactBuilder.java`
- Fact 字段填充：`src/main/java/com/coolxer/model/fact/AbstractFact.java`
- 规则执行：`src/main/java/com/coolxer/service/impl/RuleEngineImpl.java`
- Kafka topic：`src/main/java/com/coolxer/config/TopicDefine.java`

## 数据模型约定

上报数据采用“配置定义字段顺序，agent 传数组值”的紧凑格式。

`common` 公共字段顺序定义在 `CheckerConfig` 中，例如 `userId`、`guid`、`startId`、`sdkVersion`、`appId`、`platform` 等。

各平台 checker 配置位于 `deploy/open_config/checker_config/`：

- `android.json`
- `ios.json`
- `h5.json`
- `wechat.json`
- `host.json`
- `server.json`

H5 的 `selfAppAllowlist` 按 appId 配置允许域名；未配置时仅允许 `common.appPackage` 对应的当前站点域名，不再使用硬编码测试域名。

upload 数据中的 Fact 类型通过命名约定反射构造：

```text
platform=android + type=DeviceData
=> com.coolxer.model.fact.android.AndroidDeviceFact

platform=ios + type=RuntimeData
=> com.coolxer.model.fact.ios.IosRuntimeFact
```

对应规则脚本也按 Fact 类名命名：

```text
AndroidDeviceFact -> AndroidDeviceFactRule.groovy
IosRuntimeFact   -> IosRuntimeFactRule.groovy
```

## 接口与安全边界

本仓库不再维护独立的 `doc/` 副本，完整字段与协议定义以[阅读导航](#阅读导航)中的集中式文档为准。

支持安全通信 v1 的 Agent 统一使用 `/sc/v1/handshake`、
`/sc/v1/handshake/finish` 和 `/sc/v1/message`。下表是解密后允许转发的逻辑路由，
不能以明文直接访问：

| 方法 | 推荐路径 | 说明 |
| --- | --- | --- |
| POST | `/attach/start` | Agent 启动，获取对应平台的 checker 配置并执行启动规则 |
| POST | `/attach/upload` | Agent 上报采集数据，执行对应 Fact 规则并返回 ACK |
| POST | `/cross/info` | 设备信息查询 |
| POST | `/cross/rating` | 风险评级查询 |
| POST | `/cross/label_start` | 当次启动标签查询 |

H5、Host、Android、iOS 和 Server SDK 使用协议 v1。微信小程序当前不支持该协议，不能连接新版生产服务端。

## 配置说明

### Profile 与动态配置

默认 profile：

```properties
spring.profiles.active=dev
```

开发环境关键配置：

```properties
server.port=11099
rule.engine.groovy.path=src/main/java/com/coolxer/rule/
checker.config.path=deploy/open_config/checker_config/
punish.config.path=deploy/open_config/punish_config/punish_config.json
msg.api.config.path=deploy/open_config/msg_api_config/msg_api_config.json
spring.kafka.bootstrap-servers=127.0.0.1:9094
```

开发环境直接读取源码规则目录；生产环境通过 Compose 将 `deploy/open_config/*` 只读挂载到 `/app/config/*`。规则、checker、punish 和 msg API 配置必须作为同一发布版本管理。

### 代理、来源与跨域

Host 探针可在 common 中提交经第三方服务查询的公网 IP；服务端仅在它是合法公网 IPv4/IPv6 时保留，否则使用连接来源地址。实际 TCP/可信代理来源仍独立记录在 Fact 的 `requestIp` 中。其他平台的外网 IP 继续以服务端收到的连接地址为准。只有请求的直接来源匹配 `synap.network.trusted-proxies` 时，才会解析标准 `Forwarded`、`X-Forwarded-For` 或 `X-Real-IP`，并从代理链右侧向左剥离可信节点。例如：

```properties
synap.network.trusted-proxies=10.20.0.0/16,2001:db8:100::/48
```

生产环境通过 `SYNAP_TRUSTED_PROXIES` 配置实际网关 CIDR；服务直接暴露时保持为空。不要为省事配置 `0.0.0.0/0`，否则客户端可伪造来源 IP。代理链解析结果写入 Fact 的 `requestIp`；除通过公网地址校验的 Host 声明值外，服务端会用该来源地址覆盖 `common.wanIp`。

生产环境必须显式配置浏览器 Origin，空值会拒绝跨域请求：

```properties
SYNAP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

### 安全通信与风险状态

生产 profile 的安全通信协议 v1 默认关闭；启用时必须同时提供 Redis、允许的公开 `appId`、服务端
P-256 身份密钥、会话记录加密密钥和管理接口令牌：

```properties
SYNAP_SC_ENABLED=true
SYNAP_SC_REQUIRE_TLS=true
SYNAP_SC_SERVER_KEY_ID=server-key-2026
SYNAP_SC_SERVER_PRIVATE_KEY=<PKCS8 Base64URL，由部署密钥系统注入>
SYNAP_SC_SERVER_PUBLIC_KEY=<SPKI Base64URL>
SYNAP_SC_SESSION_RECORD_KEY=<32 字节 Base64URL>
SYNAP_SC_ALLOWED_APP_IDS=synap-h5,synap-host,synap-android,synap-ios
SYNAP_SC_ENROLLMENT_ADMIN_TOKEN=<管理面长随机令牌>
```

外部数据入口只有 `/sc/v1/handshake`、`/sc/v1/handshake/finish` 和
`/sc/v1/message`。解密后只转发 `/attach/start`、`/attach/upload`、
`/cross/{id}`；直接明文访问业务 Controller 会返回 404。原生端注册令牌由受管理的
`POST /admin/sc/v1/enrollment-tokens` 签发，且只能使用一次。

可信网关还可以注入 `X-Synap-Network-ASN`、`X-Synap-Network-Category`、`X-Synap-Tls-Fingerprint` 和 `X-Synap-Ip-Reputation`。这些头只有在直接来源命中 `SYNAP_TRUSTED_PROXIES` 时才会被接受；浏览器直连提交的同名头会被忽略。

H5 握手使用 Origin 白名单准入；`Referer/Sec-Fetch-*` 仅是外围浏览器信号，不是
密码学身份凭据。解密后的业务请求继续校验受保护 `code`、`header/common` 一致性、
Fact 数量、字符串长度和嵌套深度。生产使用互相隔离的 Redis 命名空间保存安全会话、
重放窗口、安装身份、注册令牌以及原有 messageId 幂等和业务 sequence。

风险输出统一包含 `detectorVersion`、`ruleVersion`、`reasonCodes`、`evidenceCount` 和 `confidence`。新规则默认使用 `observation=true`；分数按会话聚合并随时间衰减，单个客户端信号不会直接触发高风险处罚。

## 本地开发

前置依赖：

- JDK 17 和 Maven。
- 可访问的 Kafka。
- 可访问的 Redis；dev profile 的业务幂等、sequence 和风险状态使用内存，但安全通信会话仍使用 Redis。
- 使用 `/cross/*` 时，可访问 `deploy/open_config/msg_api_config/` 中配置的下游服务。

dev profile 的安全通信配置直接维护在 `src/main/resources/application-dev.properties` 中。开发密钥必须在服务重启后保持不变，否则 Redis 中已有会话将无法解密；不要在该文件中使用生产密钥。

在仓库根目录启动：

```bash
mvn spring-boot:run
```

默认启用 `dev` profile，监听 `11099`。启动失败时优先检查 Kafka、Redis，以及 `deploy/open_config/` 下的动态配置文件。

运行测试：

```bash
mvn test
```

## 生产部署

本项目使用 `deploy/docker-compose.yml` 部署 `synap-server`。容器内部端口固定为 `11099`，对外端口由 `SYNAP_PORT` 配置。

生产环境不应把 Redis、Kafka、服务端私钥或管理令牌写入镜像、Git 仓库或 Compose 文件。本仓库的 `.env` 只是配置模板，真实密钥应由密钥管理系统或服务器上的受限文件注入。

Compose 运行环境支持：

- Docker Compose v2，使用 `docker compose`，推荐。
- Legacy `docker-compose` 1.29.2，使用 `docker-compose`。
- 不支持低于 1.29.2 的 Compose v1。

以下示例默认使用 Compose v2。使用 legacy 1.29.2 时，将命令中的 `docker compose` 替换为 `docker-compose`，其余参数保持不变。项目名通过环境文件中的 `COMPOSE_PROJECT_NAME` 设置；不要在 Compose 文件中重新加入 v1 无法识别的顶层 `name:`。

### 1. 部署架构

生产环境需要事先准备：

- Linux 服务器、Docker Engine，以及 Docker Compose v2（推荐）或 legacy `docker-compose` 1.29.2。
- 可用的 Redis，用于幂等、sequence、风险状态和安全通信会话。
- 可用的 Kafka，分区数和副本数不能超过 Kafka 集群能力。
- Asset、Operation、Risk 和 Zenvis 等下游服务。
- 可拉取 synap-server 镜像的镜像仓库凭据。
- HTTPS 入口网关或负载均衡器。不要将明文 `11099` 直接暴露到公网。

建议只允许入口网关访问 `SYNAP_PORT`，Redis、Kafka 和下游服务使用内网连接。

### 2. 构建并推送镜像

构建机需要 JDK 17、Maven 和 Docker。构建、测试均以 JDK 17 为基线，不要切换到未经验证的更高 JDK。

在 `synap-server` 根目录执行：

```bash
java -version

IMAGE_ARCH=amd64 \
SKIP_TESTS=false \
PUSH_IMAGE=true \
./build.sh
```

ARM64 服务器将 `IMAGE_ARCH` 改为 `arm64`。构建脚本会生成两个带架构后缀的标签：

```text
<IMAGE_NAME>:1.0.0.alpha-amd64
<IMAGE_NAME>:<YYYYMMDD>-amd64
```

`build.sh` 当前将版本标签固定为 `1.0.0.alpha`，同时生成日期标签；传入 `IMAGE_TAG` 不会覆盖它。生产发布应在脚本中更新版本并使用不可变标签，不要依赖 `latest`。如需使用其他镜像仓库，可指定 `IMAGE_NAME` 和 `BASE_IMAGE_REPOSITORY`。

### 3. 准备动态配置

Compose 会将 `open_config` 下的配置以只读方式挂载到容器。制作发布包时必须检查：

```text
deploy/open_config/
├── identify_config/    # Groovy 规则
├── checker_config/     # Agent checker 配置
├── punish_config/      # 处置动作配置
└── msg_api_config/     # 外部 API 配置
```

`identify_config` 不能为空。`build.sh` 会在构建前自动同步源码规则；不通过该脚本制作发布包时，手动执行：

```bash
cp src/main/java/com/coolxer/rule/*.groovy deploy/open_config/identify_config/
ls -l deploy/open_config/identify_config/
```

规则和 checker/punish 配置必须与应用镜像版本匹配。升级前应将它们与 Compose 文件一起备份。

### 4. 生成安全通信密钥

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

### 5. 准备生产环境变量

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

#### 代理、H5 与微信准入参数

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

#### 安全通信 v1 参数

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

### 6. 准备 Docker 网络

Compose 使用外部网络 `ZENVIS_NETWORK_NAME`。先确认该网络已由 Zenvis 或基础设施部署创建：

```bash
docker network inspect deploy_zenvis-network
```

如果尚未创建且确定应使用该名称，可执行：

```bash
docker network create deploy_zenvis-network
```

Kafka、Redis 和其他使用服务名访问的下游容器必须加入同一网络。

### 7. 部署前检查

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

### 8. 启动和验证

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

### 9. 升级和回滚

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

### 10. 停止服务

```bash
docker compose \
  --env-file /etc/synap/synap-server.env \
  -f deploy/docker-compose.yml \
  down
```

Compose 中的 Zenvis 网络是外部网络，`down` 不会删除它。Redis 和 Kafka 数据也不在本 Compose 项目内，其备份和恢复应由对应基础设施流程负责。

## 扩展 Fact 与规则

1. 在 `deploy/open_config/checker_config/<platform>.json` 中增加 `<type>Config`。
2. 在 `src/main/java/com/coolxer/model/fact/<platform>/` 下新增 `<Platform><Type>Fact.java`。
3. 如需自动字段填充，在字段上添加 `@ValueSet`，并确保字段名与配置 key 对应。
4. 在 `src/main/java/com/coolxer/rule/` 下新增 `<Platform><Type>FactRule.groovy`，返回 `ArrayList<Agenda>`；发布时由 `build.sh` 同步到生产规则目录。
5. 在 `src/main/java/com/coolxer/config/TopicDefine.java` 中新增对应 Kafka topic 常量和 `NewTopic` Bean。
6. 更新 Synap 主仓库的集中接口文档和必要测试。

## 开发约束与已知行为

- `FactBuilder` 依赖平台名和 `type` 的命名约定；命名不一致会导致 Fact 构造失败并跳过该条数据。
- `punish` 使用 `HashSet` 去重，返回顺序当前不保证稳定，代码中也保留了按优先级排序的 TODO。
- 支持 ACK 的 Agent 使用“至少一次传输 + messageId 幂等消费”；Redis `done` 记录默认保存 14 天，处理中租约为 5 分钟。安全 SDK 不自动重试业务 POST。
- H5 的 `appId` 是公开标识；身份保证依赖 HTTPS、服务端公钥固定、Origin 白名单和不可导出的 WebCrypto 安装密钥，强度低于原生端的一次性令牌注册。
- `cross` 接口找不到配置 id 时会抛出 `IllegalArgumentException`。
