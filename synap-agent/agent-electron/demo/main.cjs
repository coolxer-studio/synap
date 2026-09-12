const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { app, BrowserWindow, net, protocol } = require('electron');
const { registerElectronAgent } = require('../dist/main.cjs');

const SCHEME = 'synap-electron';
const ORIGIN = `${SCHEME}://app`;

protocol.registerSchemesAsPrivileged([{
  scheme: SCHEME,
  privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true }
}]);

function localResource(request) {
  const pathname = new URL(request.url).pathname;
  const files = new Map([
    ['/demo/index.html', path.join(__dirname, 'index.html')],
    ['/demo/renderer.js', path.join(__dirname, 'renderer.js')],
    ['/dist/renderer.js', path.join(__dirname, '..', 'dist', 'renderer.js')]
  ]);
  const file = files.get(pathname);
  return file ? net.fetch(pathToFileURL(file).toString()) : new Response('Not found', { status: 404 });
}

app.whenReady().then(async () => {
  protocol.handle(SCHEME, localResource);
  const mainWindow = new BrowserWindow({
    width: 960,
    height: 720,
    webPreferences: {
      preload: path.join(__dirname, '..', 'dist', 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true
    }
  });
  const registration = registerElectronAgent(mainWindow, { allowedOrigins: [ORIGIN] });
  mainWindow.on('closed', () => registration.dispose());
  await mainWindow.loadURL(`${ORIGIN}/demo/index.html`);
});

app.on('window-all-closed', () => app.quit());
