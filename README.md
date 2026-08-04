# synap-agent

synap-agent 是 synap 的客户端探针与数据仿真工具集，用来采集终端侧环境、运行态和用户行为数据，并把数据上报给 synap-server 或兼容的接入网关。

当前仓库包含两个主要模块：

- `agent-h5`：浏览器 H5 探针 SDK，编译后以 `probe.*.js` 的形式接入 Web 页面。
- `agent-emulator`：Go 实现的多端数据仿真器，用于加密样例报文，或模拟 Android、iOS、H5、微信小程序、主机端数据上报。

## 代码分析摘要

### H5 探针

`agent-h5/src/index.js` 是 H5 SDK 的入口。页面加载探针后会执行以下流程：

1. 创建 `Dispatch`，读取内置的 app key 与后端地址。
2. 通过 start 接口发送基础 `common` 信息，获取服务端下发的 checker 配置。
3. 解密配置后初始化采集模块。
4. 按策略周期采集设备信息、环境校验、运行态、异常状态和用户行为。
5. 将采集结果放入 `window.probeMsgQueue`，定时批量加密上报。
6. 向页面暴露统一的 `window.SynapAgent` Promise API，并保留旧 `window.*` 兼容接口。

主要采集模块：

| 模块 | 入口文件 | 说明 |
| --- | --- | --- |
| 设备信息 | `src/lib/device-info/ct-device-info.js` | 采集系统、屏幕、CPU、电池、网络、存储、浏览器、WebGL/Canvas 指纹等 |
| 运行态校验 | `src/lib/running-check/ct-running-check.js` | 采集站点来源、地理位置、运行环境、调试状态等 |
| 异常状态 | `src/lib/illegal-state/ct-illegal-state.js` | 监听 `window.onerror` 和 `console.error` |
| 用户行为 | `src/lib/user-behavior/ct-user-behavior.js` | 监听页面事件和开发者工具行为 |
| 基础数据 | `src/lib/base-data.js` | 生成 `common` 数组和 `code` 请求头 |
| 安全通信 | `src/lib/secure-communication/ajax-http.js` | 独立 XHR、超时和错误分类；报文编解码由固定版本安全通信包提供 |

### 数据仿真器

`agent-emulator/main.go` 提供两种模式：

- 无命令行参数：读取 `data/<platform>/start` 与 `data/<platform>/upload`，加密后写入 `data_encryp/<platform>/`。
- 有命令行参数：读取样例报文，替换 UUID、start_id、客户端时间等字段，然后发送到后端。

发送模式会遍历 `android`、`ios`、`h5`、`wechat`、`host` 等目录中的样例数据，对每个设备依次发送 start 和 upload 报文。

## 目录结构

```text
synap-agent
├── README.md
├── LICENSE
├── agent-h5
│   ├── src                     # H5 探针源码
│   │   ├── index.js            # SDK 入口、启动流程、全局 API 暴露
│   │   └── lib                 # 采集、通信、存储、加密等模块
│   ├── config                  # Webpack 构建配置
│   ├── demo                    # 本地演示页面
│   ├── doc/接入-Web.md         # Web 接入说明
│   ├── package.json
│   └── package-lock.json
└── agent-emulator
    ├── main.go                 # 数据加密与模拟发送入口
    ├── data                    # 原始样例报文
    ├── data_encryp             # 加密后的样例报文
    ├── start_sender.sh         # Docker/后台循环发送示例
    ├── go.mod
    └── go.sum
```

## 与 synap-server 的关系

synap-agent 负责采集和发送数据，synap-server 负责接收数据、构造 Fact、执行规则并返回 agenda/punish。

典型流程：

```text
agent-h5 / agent-emulator
  -> start
  -> synap-server 下发 checker 配置
  -> agent 按配置采集数据
  -> upload
  -> synap-server 执行规则并返回处理结果
```

synap-server 默认端口为 `11099`。后端 Controller 中的主要路由是：

| 用途 | 语义化路径 | 数字别名 |
| --- | --- | --- |
| 启动并获取配置 | `/attach/start` | `/1/1` |
| 上报采集数据 | `/attach/upload` | `/1/2` |
| 查询外部信息 | `/cross/{id}` | `/2/{id}` |

`agent-emulator` 默认直接调用数字别名：`http://{host}:11099/1/1` 和 `http://{host}:11099/1/2`。

`agent-h5` 当前代码内置的默认地址为：

```text
http://127.0.0.1:11099/sc/h5/attach/start
http://127.0.0.1:11099/sc/h5/attach/upload
http://127.0.0.1:11099/sc/h5/cross/info
http://127.0.0.1:11099/sc/h5/cross/rating
http://127.0.0.1:11099/sc/h5/cross/label_start
```

如果直接对接当前仓库中的 synap-server，需要确认网关是否会把 `/sc/h5/...` 转发到对应的 `/attach/...` 与 `/cross/...`，或者在 `agent-h5/src/index.js` 中调整地址。

## H5 探针使用

### 环境要求

- Node.js 18+
- npm

`agent-h5/package.json` 使用 Webpack 5、Babel 和统一的安全通信 SDK。

### 安装依赖

```bash
cd agent-h5
npm ci
```

### 开发构建

```bash
npm run dev
```

开发模式会监听源码变化，输出 `dist/probe.dev.js`，并把它复制到 `demo/js/probe.js`，便于打开 `demo/index.html` 或 `demo/tools.html` 调试。

### 生产构建

```bash
npm run build
```

生产模式输出：

```text
dist/probe.min.js
```

### 页面接入

把构建产物放到业务页面可访问的位置，然后引入脚本：

```html
<script>
  window.SynapAgentConfig = {
    appKey: '申请得到的 APP KEY',
    baseUrl: 'https://risk.example.com',
    collectionProfile: 'full'
  };
</script>
<script type="text/javascript" src="./js/probe.min.js"></script>
```

SDK 会自动启动，也可使用统一 Promise API 显式、幂等地初始化：

```js
await window.SynapAgent.init(window.SynapAgentConfig);
```

### 页面可调用 API

#### `window.userData(userId, data)`

发送业务自定义数据。`userId` 建议传脱敏后的用户标识，`data` 可以是业务自定义对象。

```js
if (typeof window.userData === 'function') {
  window.userData(md5UserId, {
    scene: 'login',
    channel: 'web'
  });
}
```

函数返回 `boolean`。不得上传密码、证件号、支付信息等明文敏感数据。

#### `window.deviceInfo()`

同步查询接口仅为兼容保留，已废弃。新代码使用：

```js
var data = await window.SynapAgent.getDeviceInfo();
```

#### `window.ratingInfo()`

新代码使用 Promise API：

```js
var data = await window.SynapAgent.getRatingInfo();
```

#### `window.labelInfo()`

新代码使用 Promise API：

```js
var data = await window.SynapAgent.getLabelInfo();
```

## 数据仿真器使用

### 环境要求

- Go 1.21+

### 构建

```bash
cd agent-emulator
go build -o agent-emulator
```

### 生成加密样例数据

不带参数运行时，程序会读取 `./data` 并生成 `./data_encryp`：

```bash
./agent-emulator
```

加密密钥优先读取环境变量 `AGENT_EMULATOR_KEY`。未设置时使用代码中的默认密钥。

```bash
AGENT_EMULATOR_KEY=your-32-byte-key ./agent-emulator
```

### 模拟发送数据

```bash
./agent-emulator --host localhost --device_count 1 --print_log=true
```

参数说明：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `--host` | `localhost` | synap-server 主机名或 IP，不包含端口 |
| `--device_count` | `1` | 每个平台模拟的设备数量 |
| `--print_log` | `false` | 是否打印请求 URL、Header、Body 和响应 |

发送模式的数据目录选择顺序：

1. 优先读取 `./data`，按明文 JSON 处理。
2. 如果 `./data` 不存在，读取 `./data_encryp`，按加密内容解密后处理。
3. 如果前两者都不存在，尝试读取 Docker 场景下的 `/usr/local/data/`。

### 循环发送

`start_sender.sh` 会每小时随机模拟 1 到 10 个设备，并发送到名为 `synap-server` 的主机：

```bash
cd agent-emulator
sh start_sender.sh
```

## 数据格式约定

上报数据采用紧凑数组格式，字段顺序由服务端 checker 配置定义。

基础结构：

```json
{
  "common": [],
  "data": []
}
```

`common` 数组中的关键索引：

| 索引 | 字段 |
| --- | --- |
| 0 | userId |
| 1 | guid |
| 2 | startId |
| 3 | sdkVersion |
| 4 | appId |
| 5 | appName |
| 6 | appPackage |
| 7 | appVersion |
| 8 | platform |
| 23 | clientTime |

`data` 数组中的每一项通过 `type` 区分 Fact 类型，例如：

- `DeviceData`
- `SelfAppData`
- `LocationData`
- `RuntimeData`
- `ErrorData`
- `EventData`
- `DevToolData`
- `UserData`

服务端会根据 `common.platform` 和 `type` 反射构造 Fact，例如：

```text
platform=h5 + type=DeviceData
=> com.coolxer.model.fact.h5.H5DeviceFact
```

## 当前注意事项

- `agent-h5` 默认自动启动，同时暴露 `SynapAgent.init/destroy`；`initWithKey(KEY)` 作为兼容别名保留。
- `agent-h5` 默认对请求体做 SM4 加密，并在密文后追加大写 app key；当前 synap-server Controller 代码接收的是 JSON `RequestVO`，因此实际部署时需要确认前置网关或服务端解密链路是否匹配。
- `agent-h5` 的 `startConfig[1]` 仍作为服务端兼容字段保留，当前单文件构建不会动态加载 core 文件。
- `agent-emulator` 当前请求头 `code` 的拼接逻辑位于 `sendDataToAPI`，如服务端规则依赖 `Header.platform`，需要确认该 header 与 `common.platform` 是否一致。
- `agent-emulator` 的样例数据会在发送前被原地修改内存中的 UUID、startId 和时间；一次进程内多设备发送会共享同一份读入的报文对象并逐轮覆盖这些字段。

## 相关文档

- H5 接入细节：`agent-h5/doc/接入-Web.md`
- H5 子模块说明：`agent-h5/README.md`
- 数据仿真器说明：`agent-emulator/README.md`
- 服务端接口说明：`../synap-server/doc/API.md`
