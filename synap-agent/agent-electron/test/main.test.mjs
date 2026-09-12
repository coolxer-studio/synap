import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import test from 'node:test';
import { createMainRuntime } from '../src/main-runtime.js';
import { CHANNELS } from '../src/shared.js';

class FakeIpcMain {
  handlers = new Map();
  handle(name, handler) { this.handlers.set(name, handler); }
  removeHandler(name) { this.handlers.delete(name); }
}

function fixture(url = 'synap-electron://app/index.html') {
  const app = new EventEmitter();
  app.getName = () => 'Demo'; app.getVersion = () => '1.2.3'; app.getLocale = () => 'zh-CN'; app.isPackaged = true;
  const webContents = new EventEmitter();
  webContents.id = 7; webContents.mainFrame = { url };
  webContents.getURL = () => url; webContents.isDestroyed = () => false;
  webContents.getLastWebPreferences = () => ({ nodeIntegration: false, contextIsolation: true, sandbox: true, webSecurity: true, allowRunningInsecureContent: false, devTools: true });
  webContents.isDevToolsOpened = () => false; webContents.backgroundThrottling = true;
  webContents.sent = []; webContents.send = (channel, value) => webContents.sent.push([channel, value]);
  const window = new EventEmitter(); window.webContents = webContents;
  const ipcMain = new FakeIpcMain();
  const api = createMainRuntime({ app, ipcMain, now: (() => { let value = 1000; return () => ++value; })(), runtimeInfo: { platform: 'darwin', arch: 'arm64', release: '25.0', versions: { electron: '42', chrome: '142', node: '22' } } });
  return { app, webContents, window, ipcMain, api };
}

test('authorizes only the registered main frame and exact origin', async () => {
  const value = fixture();
  const registration = value.api.registerElectronAgent(value.window, { allowedOrigins: ['synap-electron://app'] });
  const handler = value.ipcMain.handlers.get(CHANNELS.context);
  const context = await handler({ sender: value.webContents, senderFrame: value.webContents.mainFrame });
  assert.equal(context.hostContext.type, 'electron');
  assert.equal(context.hostContext.security.contextIsolation, true);
  assert.deepEqual(Object.keys(context.hostContext.page), ['origin', 'path']);
  await assert.rejects(() => handler({ sender: value.webContents, senderFrame: { url: value.webContents.mainFrame.url } }), /not authorized/);
  value.webContents.mainFrame.url = 'https://attacker.example/';
  await assert.rejects(() => handler({ sender: value.webContents, senderFrame: value.webContents.mainFrame }), /not authorized/);
  assert.equal(registration.getMetrics().rejectedIpc, 2);
  registration.dispose();
});

test('buffers lifecycle events until renderer acknowledgment', async () => {
  const value = fixture();
  const registration = value.api.registerElectronAgent(value.window, { allowedOrigins: ['synap-electron://app'] });
  value.webContents.emit('unresponsive', {});
  assert.equal(value.webContents.sent[0][0], CHANNELS.event);
  const event = value.webContents.sent[0][1];
  const contextHandler = value.ipcMain.handlers.get(CHANNELS.context);
  const ackHandler = value.ipcMain.handlers.get(CHANNELS.ack);
  const ipcEvent = { sender: value.webContents, senderFrame: value.webContents.mainFrame };
  assert.equal((await contextHandler(ipcEvent)).pendingEvents.length, 1);
  assert.equal((await ackHandler(ipcEvent, [event.id])).acknowledged, 1);
  assert.equal(registration.getMetrics().queuedEvents, 0);
  registration.dispose();
});

test('rejects file and insecure HTTP origins', () => {
  assert.throws(() => fixture('file:///tmp/index.html').api.registerElectronAgent(fixture().window, { allowedOrigins: ['file:///tmp'] }), /HTTPS or secure/);
  const value = fixture();
  assert.throws(() => value.api.registerElectronAgent(value.window, { allowedOrigins: ['http://localhost:3000'] }), /HTTPS or secure/);
});
