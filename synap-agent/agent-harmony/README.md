# agent-harmony

Synap 的 HarmonyOS NEXT 安全探针。项目使用 ArkTS、Stage 模型，最低兼容
HarmonyOS 5.0.0（API 12），交付 `probe` HAR 和 `entry` 示例 HAP。

> 当前 `synap-server` 与安全通信服务端尚未注册 `HARMONY/harmony`。本 SDK 不会伪装
> Android；在服务端完成平台、Fact 和 checker 配置适配前，只能连接测试 mock，不能
> 接入现有生产服务。

## 功能

- 安全通信协议 v1：P-256、HKDF-SHA256、AES-256-GCM；
- HUKS 中不可导出的安装签名密钥和稳定的应用级设备 ID；
- 有界持久队列、240 KiB 分批、ACK 幂等消费和指数退避；
- `HarmonyDevice`、`HarmonySelfApp`、`HarmonyRuntime`、`HarmonyNetwork`、
  `HarmonyIntegrity` 基线 Fact；
- 宿主主动调用的 `userData` 和 `markAction`；
- 设备、评分和标签查询。

首版不采集位置、联系人、安装应用列表，不扫描任意文件，不拦截 UI，不捕获崩溃，
也不申请持续后台运行。

## 接入

将构建出的 HAR 放入宿主工程，并在 `oh-package.json5` 中声明：

```json5
{
  "dependencies": {
    "@coolxer/synap-agent-harmony": "file:./libs/synap-agent-harmony.har"
  }
}
```

宿主模块需要声明网络权限：

```json5
{
  "module": {
    "requestPermissions": [
      { "name": "ohos.permission.INTERNET" },
      { "name": "ohos.permission.GET_NETWORK_INFO" }
    ]
  }
}
```

```ts
import type { common } from '@kit.AbilityKit';
import { SynapAgent } from '@coolxer/synap-agent-harmony';

const agent = await SynapAgent.initialize(context as common.UIAbilityContext, {
  baseUrl: 'https://risk.example.com',
  appId: 'my-harmony-app',
  serverTrustAnchors: {
    'server-key-2026': '<P-256 SPKI Base64URL>'
  },
  enrollmentTokenProvider: {
    async getToken(): Promise<string | undefined> {
      return await fetchOneTimeEnrollmentToken();
    }
  },
  collectionProfile: 'baseline',
  expectedSigningCertificateSha256: ['<lowercase SHA-256 hex>']
});

await agent.start();
agent.userData('business-user-id', { tier: 'gold' });
agent.markAction('checkout-confirm');
```

在宿主进入后台时停止周期任务，回到前台时重新启动：

```ts
onBackground(): void {
  agent.stop({ flush: true }).catch(() => {});
}

onForeground(): void {
  agent.start().catch(() => {});
}
```

`stop` 保留内存会话和待发送队列；`destroy` 关闭会话并释放单例，但不会删除 HUKS
安装身份或待重试消息。同一配置重复 `initialize` 返回同一实例；更改配置前必须先
`destroy`。

HTTP 默认被拒绝。仅本机 mock 可显式设置 `allowInsecureHttp: true`，且地址必须是
`localhost`、`127.0.0.1` 或 `[::1]`。

## 公共 API

```text
SynapAgent.initialize(context, config)
agent.start()
agent.stop({ flush? })
agent.destroy({ flush? })
agent.flush()
agent.userData(userId, data)
agent.markAction(action, data?)
agent.getDeviceInfo()
agent.getRatingInfo()
agent.getLabelInfo()
agent.getMetrics()
```

错误统一为 `SynapError`，提供 `code`、`httpStatus`、可选 `traceId` 和 `cause`。日志和
错误不会包含密钥、注册令牌、业务正文或完整密文信封。

## 构建与测试

先用 DevEco Studio 安装 HarmonyOS 5.0.0（API 12）或更高 SDK，然后在本目录执行：

```bash
ohpm install
hvigorw clean
hvigorw --mode module -p module=probe@default -p product=default assembleHar
hvigorw --mode module -p module=entry@default -p product=default assembleHap
hvigorw --mode module -p module=probe@ohosTest test
```

没有 HarmonyOS SDK 时，可以运行只读结构检查：

```bash
node tools/verify-project.mjs
```

协议向量固定副本来自 `secure-communication/protocol/test-vectors`。更新协议实现时必须
重新同步向量，并在模拟器或真机验证断网恢复、进程重启恢复队列、`stop/start` 和退
后台不继续周期运行。

## 隐私边界

- SDK 从不主动申请运行时权限；不可用字段填写空值或 `unknown`。
- `userData` 最大 32 KiB，并拒绝名称疑似密码、令牌、Cookie、证件、银行卡、电话或
  邮箱的字段。
- enrollment token 只保留在内存，握手完成后立即清除。
- 生产环境必须使用 HTTPS 和可信构建渠道下发的服务端 P-256 SPKI 信任锚。

