import { BRIDGE_NAME, CHANNELS, deepFreeze } from './shared.js';

export function installPreloadBridge({ contextBridge, ipcRenderer }) {
  let eventListener = null;
  const bridge = Object.freeze({
    getContext: async () => deepFreeze(await ipcRenderer.invoke(CHANNELS.context)),
    ackEvents: async (ids) => {
      const normalized = Array.isArray(ids) ? ids.slice(0, 32).map(String) : [];
      return deepFreeze(await ipcRenderer.invoke(CHANNELS.ack, normalized));
    },
    subscribe(callback) {
      if (typeof callback !== 'function') throw new TypeError('Synap Electron event callback must be a function');
      if (eventListener) ipcRenderer.removeListener(CHANNELS.event, eventListener);
      eventListener = (_event, payload) => callback(deepFreeze(payload));
      ipcRenderer.on(CHANNELS.event, eventListener);
      return true;
    },
    unsubscribe() {
      if (eventListener) ipcRenderer.removeListener(CHANNELS.event, eventListener);
      eventListener = null;
    }
  });
  contextBridge.exposeInMainWorld(BRIDGE_NAME, bridge);
  return bridge;
}
