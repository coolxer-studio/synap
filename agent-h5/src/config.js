export const PLATFORM = 'h5';
export const APP_ID = 1;
export const SDK_VERSION = '1.3.0';
export const COMMON_FIELD_COUNT = 24;
export const DEFAULT_APP_ID = '';
export const DEFAULT_BASE_URL = 'http://127.0.0.1:11099';
export const DEFAULT_SERVER_TRUST_ANCHORS = Object.freeze({});

export const ENDPOINT_PATHS = Object.freeze({
  start: '/attach/start',
  upload: '/attach/upload',
  deviceInfo: '/cross/info',
  rating: '/cross/rating',
  label: '/cross/label_start'
});

export const DEFAULT_COLLECTION_PROFILE = Object.freeze({
  canvas: false,
  webgl: false,
  fonts: false,
  webrtc: false,
  location: false,
  userBehavior: true,
  rawEvents: false,
  debug: true,
  integrity: true,
  session: true,
  network: false,
  uaHighEntropy: false,
  securityReports: true,
  resourceIntegrity: true
});

export const FULL_COLLECTION_PROFILE = Object.freeze(Object.keys(DEFAULT_COLLECTION_PROFILE)
  .reduce((profile, key) => ({ ...profile, [key]: true }), {}));

export const DEFAULT_OPTIONS = Object.freeze({
  appId: DEFAULT_APP_ID,
  baseUrl: DEFAULT_BASE_URL,
  collectionProfile: DEFAULT_COLLECTION_PROFILE,
  requestTimeout: 15000,
  queueLimit: 50,
  batchSize: 50,
  maxUserDataBytes: 32768,
  allowedScriptOrigins: [],
  allowedParentOrigins: [],
  allowedServiceWorkerOrigins: [],
  expectedScriptIntegrity: '',
  serverTrustAnchors: DEFAULT_SERVER_TRUST_ANCHORS
});

export function isLoopbackHttpUrl(baseUrl) {
  try {
    const parsed = new URL(String(baseUrl));
    const host = parsed.hostname.toLowerCase();
    return parsed.protocol === 'http:' &&
      (host === 'localhost' || host === '127.0.0.1' || host === '[::1]');
  } catch (_) {
    return false;
  }
}

export function normalizeBaseUrl(baseUrl) {
  const normalized = String(baseUrl || DEFAULT_BASE_URL).replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(normalized)) {
    throw new Error('SynapAgent baseUrl must use http or https');
  }
  if (typeof window !== 'undefined' && window.location &&
      window.location.protocol === 'https:' && /^http:\/\//i.test(normalized)) {
    throw new Error('SynapAgent refuses an insecure HTTP endpoint on an HTTPS page');
  }
  if (/^http:\/\//i.test(normalized) && !isLoopbackHttpUrl(normalized)) {
    throw new Error('SynapAgent baseUrl must use HTTPS outside local development');
  }
  return normalized;
}

export function normalizeCollectionProfile(profile) {
  if (!profile || profile === 'low' || profile === 'low-intrusion') {
    return { ...DEFAULT_COLLECTION_PROFILE };
  }
  if (profile === 'full') return { ...FULL_COLLECTION_PROFILE };
  if (profile === 'balanced') {
    return { ...DEFAULT_COLLECTION_PROFILE, canvas: true, webgl: true, fonts: true };
  }
  if (profile === 'minimal') {
    return {
      canvas: false,
      webgl: false,
      fonts: false,
      webrtc: false,
      location: false,
      userBehavior: false,
      rawEvents: false,
      debug: false,
      integrity: false,
      session: false,
      network: false,
      uaHighEntropy: false,
      securityReports: false,
      resourceIntegrity: false
    };
  }
  if (typeof profile !== 'object') {
    throw new TypeError('collectionProfile must be "low", "balanced", "full", "minimal", or an object');
  }
  return { ...DEFAULT_COLLECTION_PROFILE, ...profile };
}

export function resolveOptions(options = {}) {
  const resolved = {
    ...DEFAULT_OPTIONS,
    ...options,
    appId: String(options.appId || DEFAULT_APP_ID),
    baseUrl: normalizeBaseUrl(options.baseUrl || DEFAULT_BASE_URL),
    collectionProfile: normalizeCollectionProfile(options.collectionProfile)
  };
  if (!resolved.appId) throw new Error('SynapAgent appId is required');
  if (!resolved.serverTrustAnchors || Object.keys(resolved.serverTrustAnchors).length === 0) {
    throw new Error('SynapAgent serverTrustAnchors is required');
  }
  ['allowedScriptOrigins', 'allowedParentOrigins', 'allowedServiceWorkerOrigins'].forEach((key) => {
    if (!Array.isArray(resolved[key])) throw new TypeError(`${key} must be an array`);
    resolved[key] = resolved[key].map(String).slice(0, 20);
  });
  resolved.expectedScriptIntegrity = String(resolved.expectedScriptIntegrity || '').slice(0, 256);
  return resolved;
}

export function hasSecurityBootstrap(options = {}) {
  const appId = String(options.appId || DEFAULT_APP_ID).trim();
  const anchors = options.serverTrustAnchors || DEFAULT_SERVER_TRUST_ANCHORS;
  return appId.length > 0 && anchors && typeof anchors === 'object' &&
    Object.keys(anchors).length > 0;
}

export function createEndpoints(baseUrl) {
  const endpoints = {};
  Object.keys(ENDPOINT_PATHS).forEach((key) => {
    endpoints[key] = ENDPOINT_PATHS[key];
  });
  return endpoints;
}

export function policyCacheKey(appId) {
  return `__synap_policy_${SDK_VERSION}_${appId}`;
}
