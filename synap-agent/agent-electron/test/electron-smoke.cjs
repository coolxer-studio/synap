const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { app, BrowserWindow, net, protocol } = require('electron');
const { registerElectronAgent } = require('../dist/main.cjs');

const scheme = 'synap-electron-smoke';
const origin = `${scheme}://app`;
protocol.registerSchemesAsPrivileged([{
  scheme,
  privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true }
}]);

let finished = false;
function finish(code, message) {
  if (finished) return;
  finished = true;
  if (message) (code === 0 ? console.log : console.error)(message);
  process.exitCode = code;
  app.quit();
}

app.whenReady().then(async () => {
  protocol.handle(scheme, (request) => {
    const pathname = new URL(request.url).pathname;
    const file = pathname === '/renderer.js'
      ? path.join(__dirname, '..', 'dist', 'renderer.js')
      : path.join(__dirname, 'fixtures', 'smoke.html');
    return net.fetch(pathToFileURL(file).toString());
  });
  const window = new BrowserWindow({
    show: false,
    webPreferences: {
      preload: path.join(__dirname, '..', 'dist', 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true
    }
  });
  const registration = registerElectronAgent(window, { allowedOrigins: [origin] });
  try {
    await window.loadURL(`${origin}/index.html`);
    const result = await window.webContents.executeJavaScript(`(async () => {
      const renderer = await import('/renderer.js');
      const value = await window.SynapElectronProbe.getContext();
      return {
        bridgeKeys: Object.keys(window.SynapElectronProbe).sort(),
        type: value.hostContext.type,
        origin: value.hostContext.page.origin,
        security: value.hostContext.security,
        nodeVisible: typeof window.process !== 'undefined',
        rendererExports: Object.keys(renderer).sort(),
        h5RecordEvent: typeof window.SynapAgent.recordEvent
      };
    })()`);
    if (result.type !== 'electron' || result.origin !== origin || result.nodeVisible
        || result.security.nodeIntegration !== false || result.security.contextIsolation !== true
        || result.security.sandbox !== true
        || result.bridgeKeys.join(',') !== 'ackEvents,getContext,subscribe,unsubscribe'
        || result.rendererExports.join(',') !== 'destroyElectronAgent,initElectronAgent'
        || result.h5RecordEvent !== 'function') {
      throw new Error(`Unexpected smoke result: ${JSON.stringify(result)}`);
    }
    registration.dispose();
    window.destroy();
    finish(0, 'Electron custom-origin/context-isolation smoke test passed');
  } catch (error) {
    registration.dispose();
    if (!window.isDestroyed()) window.destroy();
    finish(1, error.stack || error.message);
  }
});

setTimeout(() => finish(1, 'Electron smoke test timed out'), 15000);
