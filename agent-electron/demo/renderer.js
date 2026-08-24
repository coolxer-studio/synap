import { initElectronAgent } from '/dist/renderer.js';

const status = document.querySelector('#status');
const output = document.querySelector('#context');
const bridgeContext = await window.SynapElectronProbe.getContext();
output.textContent = JSON.stringify(bridgeContext.hostContext, null, 2);
status.textContent = 'Electron 安全桥工作正常；未配置 Server，暂不发送数据。';
status.className = 'ok';

// 替换公开配置后取消注释。不要把注册令牌、私钥或业务 Secret 放进 renderer。
// await initElectronAgent({
//   baseUrl: 'https://synap.example.com',
//   appId: 'synap-electron-demo',
//   serverTrustAnchors: { 'server-key-2026': '<P-256 SPKI Base64URL>' },
//   collectionProfile: 'low'
// });

void initElectronAgent;
