const SENSITIVE_KEY = /(password|passwd|pwd|token|authorization|cookie|secret|private.?key|id.?card|credit.?card|card.?number|phone|email)/i;
const EVENT_NAME = /^[a-zA-Z0-9._:-]{1,64}$/;

function byteLength(value) {
  const text = JSON.stringify(value);
  if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(text).length;
  return unescape(encodeURIComponent(text)).length;
}

function copyValue(value, state, depth) {
  if (depth > state.maxDepth) throw new TypeError('structured value exceeds maximum depth');
  if (value == null || typeof value === 'boolean') return value;
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) throw new TypeError('structured value contains a non-finite number');
    return value;
  }
  if (typeof value === 'string') return value.slice(0, state.maxStringLength);
  if (Array.isArray(value)) {
    if (value.length > state.maxArrayLength) throw new TypeError('structured value contains too many array items');
    return value.map((item) => copyValue(item, state, depth + 1));
  }
  if (typeof value !== 'object' || Object.getPrototypeOf(value) !== Object.prototype) {
    throw new TypeError('structured value must contain only plain JSON values');
  }
  const keys = Object.keys(value);
  state.keys += keys.length;
  if (state.keys > state.maxKeys) throw new TypeError('structured value contains too many keys');
  const result = {};
  keys.forEach((key) => {
    if (!key || key.length > 64) throw new TypeError('structured value contains an invalid key');
    if (SENSITIVE_KEY.test(key)) throw new TypeError(`structured value contains sensitive key: ${key}`);
    result[key] = copyValue(value[key], state, depth + 1);
  });
  return result;
}

export function normalizeStructuredValue(value, options = {}) {
  const state = {
    keys: 0,
    maxDepth: Number(options.maxDepth) || 5,
    maxKeys: Number(options.maxKeys) || 64,
    maxArrayLength: Number(options.maxArrayLength) || 32,
    maxStringLength: Number(options.maxStringLength) || 256
  };
  const result = copyValue(value, state, 0);
  if (byteLength(result) > (Number(options.maxBytes) || 16384)) {
    throw new TypeError('structured value exceeds maximum byte size');
  }
  return result;
}

export function normalizeHostContext(value) {
  if (value == null) return null;
  const normalized = normalizeStructuredValue(value, {
    maxDepth: 6,
    maxKeys: 96,
    maxArrayLength: 32,
    maxStringLength: 256,
    maxBytes: 16384
  });
  if (!normalized || normalized.type !== 'electron' || Number(normalized.schemaVersion) !== 1) {
    throw new TypeError('hostContext must be an Electron schemaVersion 1 context');
  }
  return normalized;
}

export function normalizeAdapterEvent(name, data) {
  if (!EVENT_NAME.test(String(name || ''))) {
    throw new TypeError('adapter event name must match [a-zA-Z0-9._:-]{1,64}');
  }
  return {
    name: String(name),
    detail: normalizeStructuredValue(data == null ? {} : data, {
      maxDepth: 4,
      maxKeys: 32,
      maxArrayLength: 24,
      maxStringLength: 256,
      maxBytes: 8192
    })
  };
}

export function attachHostCapabilities(capabilities, hostContext) {
  if (!hostContext || hostContext.type !== 'electron') return capabilities;
  return { ...capabilities, electron: hostContext };
}

export function electronIntegrityEvidence(hostContext) {
  if (!hostContext || hostContext.type !== 'electron') return { signals: {}, weights: {} };
  const security = hostContext.security || {};
  const app = hostContext.app || {};
  return {
    signals: {
      electronNodeIntegrationEnabled: security.nodeIntegration === true,
      electronContextIsolationDisabled: security.contextIsolation === false,
      electronSandboxDisabled: security.sandbox === false,
      electronWebSecurityDisabled: security.webSecurity === false,
      electronInsecureContentAllowed: security.allowRunningInsecureContent === true,
      electronDevToolsOpenInPackagedApp: app.packaged === true && security.devToolsOpened === true
    },
    weights: {
      electronNodeIntegrationEnabled: 5,
      electronContextIsolationDisabled: 5,
      electronSandboxDisabled: 4,
      electronWebSecurityDisabled: 5,
      electronInsecureContentAllowed: 4,
      electronDevToolsOpenInPackagedApp: 1
    }
  };
}

export function pageUrlTemplate(value) {
  try {
    const parsed = new URL(String(value || ''));
    const path = parsed.pathname.split('/').map((segment) => {
      if (/^\d{2,}$/.test(segment) || /^[0-9a-f]{8}-[0-9a-f-]{20,}$/i.test(segment)) return ':id';
      return segment;
    }).join('/');
    return `${parsed.origin === 'null' ? `${parsed.protocol}//${parsed.host}` : parsed.origin}${path}`.slice(0, 512);
  } catch (_) {
    return '';
  }
}

export function createAdapterEventFact(name, data, environment = {}) {
  const event = normalizeAdapterEvent(name, data);
  return {
    type: 'EventData',
    buildDate: String(environment.buildDate || ''),
    href: pageUrlTemplate(environment.href),
    docTitle: String(environment.docTitle || '').slice(0, 128),
    online: environment.online === false ? 0 : 1,
    data: {
      source: 'host-adapter',
      trusted: true,
      name: event.name,
      detail: event.detail
    }
  };
}
