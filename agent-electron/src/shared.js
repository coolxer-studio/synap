export const BRIDGE_NAME = 'SynapElectronProbe';

export const CHANNELS = Object.freeze({
  context: 'synap:electron:context:v1',
  ack: 'synap:electron:ack:v1',
  event: 'synap:electron:event:v1'
});

const SENSITIVE_KEY = /(password|passwd|pwd|token|authorization|cookie|secret|private.?key|id.?card|credit.?card|card.?number|phone|email|file.?path|absolute.?path|argv|environment|hostname|username|mac.?address)/i;

export function canonicalOrigin(value) {
  const parsed = new URL(String(value || ''));
  const protocol = parsed.protocol.toLowerCase();
  if (!protocol || protocol === 'file:' || protocol === 'http:' || protocol === 'data:' || protocol === 'javascript:') {
    throw new TypeError('Electron Agent requires an HTTPS or secure custom-protocol origin');
  }
  if (parsed.username || parsed.password || !parsed.hostname) throw new TypeError('Electron Agent origin is invalid');
  const host = parsed.host.toLowerCase();
  return protocol === 'https:' ? `https://${host}` : `${protocol}//${host}`;
}

export function normalizeAllowedOrigins(values) {
  if (!Array.isArray(values) || values.length === 0 || values.length > 16) {
    throw new TypeError('allowedOrigins must contain between 1 and 16 exact origins');
  }
  return new Set(values.map((value) => {
    const parsed = new URL(String(value || ''));
    const origin = canonicalOrigin(value);
    if ((parsed.pathname && parsed.pathname !== '/') || parsed.search || parsed.hash) {
      throw new TypeError('allowedOrigins entries must not contain a path, query, or hash');
    }
    return origin;
  }));
}

export function pageTemplate(value) {
  const parsed = new URL(String(value || ''));
  const origin = canonicalOrigin(value);
  const path = parsed.pathname.split('/').map((segment) => {
    if (/^\d{2,}$/.test(segment) || /^[0-9a-f]{8}-[0-9a-f-]{20,}$/i.test(segment)) return ':id';
    return segment.slice(0, 64);
  }).join('/').slice(0, 256);
  return { origin, path: path || '/' };
}

export function sanitizeValue(value, options = {}, state = { keys: 0 }, depth = 0) {
  const maxDepth = options.maxDepth || 4;
  if (depth > maxDepth) throw new TypeError('value exceeds maximum depth');
  if (value == null || typeof value === 'boolean') return value;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value === 'string') return value.slice(0, options.maxStringLength || 256);
  if (Array.isArray(value)) {
    if (value.length > (options.maxArrayLength || 32)) throw new TypeError('value contains too many array items');
    return value.map((item) => sanitizeValue(item, options, state, depth + 1));
  }
  if (typeof value !== 'object' || Object.getPrototypeOf(value) !== Object.prototype) {
    throw new TypeError('value must contain only plain JSON data');
  }
  const result = {};
  const keys = Object.keys(value);
  state.keys += keys.length;
  if (state.keys > (options.maxKeys || 64)) throw new TypeError('value contains too many keys');
  keys.forEach((key) => {
    if (!key || key.length > 64 || SENSITIVE_KEY.test(key)) throw new TypeError(`value contains forbidden key: ${key}`);
    result[key] = sanitizeValue(value[key], options, state, depth + 1);
  });
  return result;
}

export function deepFreeze(value) {
  if (!value || typeof value !== 'object' || Object.isFrozen(value)) return value;
  Object.keys(value).forEach((key) => deepFreeze(value[key]));
  return Object.freeze(value);
}

export function booleanOrNull(value) {
  return typeof value === 'boolean' ? value : null;
}
