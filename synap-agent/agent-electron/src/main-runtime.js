import {
  CHANNELS,
  booleanOrNull,
  canonicalOrigin,
  normalizeAllowedOrigins,
  pageTemplate,
  sanitizeValue
} from './shared.js';

function text(value, limit = 128) {
  return String(value == null ? '' : value).slice(0, limit);
}

function eventDetail(name, args) {
  if (name === 'render-process-gone') {
    const details = args[1] || {};
    return { reason: text(details.reason, 64), exitCode: Number(details.exitCode) || 0 };
  }
  if (name === 'child-process-gone') {
    const details = args[1] || {};
    return {
      processType: text(details.type, 64),
      reason: text(details.reason, 64),
      exitCode: Number(details.exitCode) || 0
    };
  }
  if (name === 'did-fail-load') {
    const failedUrl = args[3];
    let page = null;
    try { page = pageTemplate(failedUrl); } catch (_) { }
    return { errorCode: Number(args[1]) || 0, errorDescription: text(args[2], 128), page };
  }
  return {};
}

function createQueue(limit, now) {
  let sequence = 0;
  let items = [];
  return {
    add(name, data) {
      sequence += 1;
      const item = Object.freeze({
        id: `electron-event-${now()}-${sequence}`,
        name,
        observedAt: now(),
        data: sanitizeValue(data || {}, { maxDepth: 3, maxKeys: 24, maxArrayLength: 16, maxStringLength: 256 })
      });
      items.push(item);
      if (items.length > limit) items = items.slice(items.length - limit);
      return item;
    },
    ack(ids) {
      const accepted = new Set(Array.isArray(ids) ? ids.slice(0, limit).map(String) : []);
      const before = items.length;
      items = items.filter((item) => !accepted.has(item.id));
      return before - items.length;
    },
    all() { return items.map((item) => ({ ...item, data: { ...item.data } })); },
    size() { return items.length; }
  };
}

function buildHostContext(app, webContents, runtimeInfo) {
  const preferences = typeof webContents.getLastWebPreferences === 'function'
    ? webContents.getLastWebPreferences() || {} : {};
  const page = pageTemplate(webContents.getURL());
  return {
    type: 'electron',
    schemaVersion: 1,
    app: {
      name: text(app.getName(), 128),
      version: text(app.getVersion(), 64),
      packaged: app.isPackaged === true,
      locale: typeof app.getLocale === 'function' ? text(app.getLocale(), 32) : ''
    },
    runtime: {
      electron: text(runtimeInfo.versions && runtimeInfo.versions.electron, 32),
      chromium: text(runtimeInfo.versions && runtimeInfo.versions.chrome, 32),
      node: text(runtimeInfo.versions && runtimeInfo.versions.node, 32),
      platform: text(runtimeInfo.platform, 32),
      arch: text(runtimeInfo.arch, 32),
      release: text(runtimeInfo.release, 64)
    },
    security: {
      nodeIntegration: booleanOrNull(preferences.nodeIntegration),
      contextIsolation: booleanOrNull(preferences.contextIsolation),
      sandbox: booleanOrNull(preferences.sandbox),
      webSecurity: booleanOrNull(preferences.webSecurity),
      allowRunningInsecureContent: booleanOrNull(preferences.allowRunningInsecureContent),
      devTools: booleanOrNull(preferences.devTools),
      devToolsOpened: typeof webContents.isDevToolsOpened === 'function' && webContents.isDevToolsOpened()
    },
    page,
    window: {
      backgroundThrottling: booleanOrNull(webContents.backgroundThrottling)
    }
  };
}

export function createMainRuntime({ app, ipcMain, runtimeInfo, now = Date.now }) {
  let active = null;
  let handlersInstalled = false;

  const authorize = (event) => {
    if (!active || active.disposed) return null;
    const contents = active.window.webContents;
    if (!contents || contents.isDestroyed() || event.sender !== contents || event.senderFrame !== contents.mainFrame) {
      active.metrics.rejectedIpc += 1;
      return null;
    }
    try {
      if (!active.allowedOrigins.has(canonicalOrigin(event.senderFrame.url || contents.getURL()))) {
        active.metrics.rejectedIpc += 1;
        return null;
      }
    } catch (_) {
      active.metrics.rejectedIpc += 1;
      return null;
    }
    return active;
  };

  const installHandlers = () => {
    if (handlersInstalled) return;
    ipcMain.handle(CHANNELS.context, async (event) => {
      const registration = authorize(event);
      if (!registration) throw new Error('Synap Electron bridge sender is not authorized');
      return {
        hostContext: buildHostContext(app, registration.window.webContents, runtimeInfo),
        pendingEvents: registration.queue.all()
      };
    });
    ipcMain.handle(CHANNELS.ack, async (event, ids) => {
      const registration = authorize(event);
      if (!registration) throw new Error('Synap Electron bridge sender is not authorized');
      const acknowledged = registration.queue.ack(ids);
      registration.metrics.acknowledged += acknowledged;
      return { acknowledged };
    });
    handlersInstalled = true;
  };

  const removeHandlers = () => {
    if (!handlersInstalled) return;
    ipcMain.removeHandler(CHANNELS.context);
    ipcMain.removeHandler(CHANNELS.ack);
    handlersInstalled = false;
  };

  function registerElectronAgent(window, options = {}) {
    if (active && !active.disposed) throw new Error('Only one designated Electron window can run Synap Agent');
    if (!window || !window.webContents || window.webContents.isDestroyed()) {
      throw new TypeError('registerElectronAgent requires a live BrowserWindow');
    }
    const allowedOrigins = normalizeAllowedOrigins(options.allowedOrigins);
    const initialUrl = window.webContents.getURL();
    if (initialUrl && !allowedOrigins.has(canonicalOrigin(initialUrl))) {
      throw new Error('BrowserWindow URL is not in allowedOrigins');
    }

    const queue = createQueue(32, now);
    const listeners = [];
    const metrics = { emitted: 0, acknowledged: 0, rejectedIpc: 0 };
    const registration = { window, allowedOrigins, queue, listeners, metrics, disposed: false };
    active = registration;
    installHandlers();

    const canSend = () => {
      try {
        return !window.webContents.isDestroyed()
          && allowedOrigins.has(canonicalOrigin(window.webContents.getURL()));
      } catch (_) { return false; }
    };
    const publish = (name, ...args) => {
      if (registration.disposed) return;
      const item = queue.add(name, eventDetail(name, args));
      if (canSend()) {
        window.webContents.send(CHANNELS.event, item);
        metrics.emitted += 1;
      }
    };
    const listen = (target, name, handler) => {
      target.on(name, handler);
      listeners.push(() => target.off(name, handler));
    };

    listen(window.webContents, 'render-process-gone', (...args) => publish('render-process-gone', ...args));
    listen(window.webContents, 'unresponsive', (...args) => publish('unresponsive', ...args));
    listen(window.webContents, 'responsive', (...args) => publish('responsive', ...args));
    listen(window.webContents, 'devtools-opened', (...args) => publish('devtools-opened', ...args));
    listen(window.webContents, 'devtools-closed', (...args) => publish('devtools-closed', ...args));
    listen(window.webContents, 'did-fail-load', (...args) => {
      if (args[4] !== false) publish('did-fail-load', ...args);
    });
    listen(app, 'child-process-gone', (...args) => publish('child-process-gone', ...args));

    const dispose = () => {
      if (registration.disposed) return;
      registration.disposed = true;
      listeners.splice(0).reverse().forEach((remove) => remove());
      if (active === registration) active = null;
      removeHandlers();
    };
    listen(window, 'closed', dispose);

    return Object.freeze({
      dispose,
      getMetrics: () => Object.freeze({ ...metrics, queuedEvents: queue.size() })
    });
  }

  return Object.freeze({ registerElectronAgent });
}
