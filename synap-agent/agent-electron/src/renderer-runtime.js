import { BRIDGE_NAME } from './shared.js';

function isLoopback(hostname) {
  return hostname === 'localhost' || hostname === '::1' || hostname === '[::1]' || /^127(?:\.\d{1,3}){3}$/.test(hostname);
}

function validateRendererLocation(location) {
  const protocol = String(location && location.protocol || '').toLowerCase();
  if (!protocol || protocol === 'file:' || protocol === 'http:' || protocol === 'data:') {
    throw new Error('Synap Electron Agent requires an HTTPS or secure custom-protocol renderer');
  }
}

function validateBaseUrl(baseUrl, packaged) {
  const parsed = new URL(String(baseUrl || ''));
  if (parsed.protocol === 'https:') return;
  if (!packaged && parsed.protocol === 'http:' && isLoopback(parsed.hostname.toLowerCase())) return;
  throw new Error('Packaged Electron applications must use an HTTPS Synap endpoint');
}

function validPendingEvent(event) {
  return event && typeof event === 'object' && /^electron-event-[a-zA-Z0-9-]{1,96}$/.test(String(event.id || ''))
    && /^[a-zA-Z0-9._:-]{1,64}$/.test(String(event.name || ''))
    && Number(event.observedAt) > 0 && event.data && typeof event.data === 'object';
}

export function createRendererRuntime({ getWindow, getAgent }) {
  let initialized = false;
  let bridge = null;

  const record = async (event) => {
    if (!initialized || !validPendingEvent(event)) return false;
    const accepted = getAgent().recordEvent(event.name, {
      eventId: event.id,
      observedAt: Number(event.observedAt),
      ...event.data
    });
    if (accepted) await bridge.ackEvents([event.id]);
    return accepted;
  };

  async function initElectronAgent(options = {}) {
    const currentWindow = getWindow();
    validateRendererLocation(currentWindow.location);
    bridge = currentWindow[BRIDGE_NAME];
    if (!bridge || typeof bridge.getContext !== 'function' || typeof bridge.ackEvents !== 'function'
        || typeof bridge.subscribe !== 'function' || typeof bridge.unsubscribe !== 'function') {
      throw new Error('Synap Electron preload bridge is unavailable');
    }
    const context = await bridge.getContext();
    if (!context || !context.hostContext || context.hostContext.type !== 'electron'
        || Number(context.hostContext.schemaVersion) !== 1) {
      throw new Error('Synap Electron host context is invalid');
    }
    validateBaseUrl(options.baseUrl, context.hostContext.app && context.hostContext.app.packaged === true);

    const agent = getAgent();
    await agent.init({ ...options, hostContext: context.hostContext });
    initialized = true;
    bridge.unsubscribe();
    bridge.subscribe((event) => { record(event).catch(() => {}); });
    for (const event of Array.isArray(context.pendingEvents) ? context.pendingEvents : []) {
      await record(event);
    }
    return agent;
  }

  async function destroyElectronAgent(options = { flush: true }) {
    if (bridge) bridge.unsubscribe();
    initialized = false;
    return getAgent().destroy(options);
  }

  return Object.freeze({ initElectronAgent, destroyElectronAgent });
}

export { validateBaseUrl, validateRendererLocation, validPendingEvent };
