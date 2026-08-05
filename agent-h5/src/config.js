export const PLATFORM = 'h5';
export const APP_ID = 1;
export const SDK_VERSION = '1.3.0';
export const COMMON_FIELD_COUNT = 24;
export const DEFAULT_APP_KEY = '2396641245c4fe6ttd041565b3651245';
export const DEFAULT_BASE_URL = 'http://127.0.0.1:11099';

export const ENDPOINT_PATHS = Object.freeze({
  start: '/sc/h5/attach/start',
  upload: '/sc/h5/attach/upload',
  deviceInfo: '/sc/h5/cross/info',
  rating: '/sc/h5/cross/rating',
  label: '/sc/h5/cross/label_start'
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
  appKey: DEFAULT_APP_KEY,
  baseUrl: DEFAULT_BASE_URL,
  collectionProfile: DEFAULT_COLLECTION_PROFILE,
  requestTimeout: 15000,
  queueLimit: 50,
  batchSize: 50,
  maxUserDataBytes: 32768,
  allowedScriptOrigins: [],
  allowedParentOrigins: [],
  allowedServiceWorkerOrigins: [],
  expectedScriptIntegrity: ''
});

export function normalizeBaseUrl(baseUrl) {
  const normalized = String(baseUrl || DEFAULT_BASE_URL).replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(normalized)) {
    throw new Error('SynapAgent baseUrl must use http or https');
  }
  if (typeof window !== 'undefined' && window.location &&
      window.location.protocol === 'https:' && /^http:\/\//i.test(normalized)) {
    throw new Error('SynapAgent refuses an insecure HTTP endpoint on an HTTPS page');
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
    appKey: String(options.appKey || DEFAULT_APP_KEY),
    baseUrl: normalizeBaseUrl(options.baseUrl || DEFAULT_BASE_URL),
    collectionProfile: normalizeCollectionProfile(options.collectionProfile)
  };
  if (!resolved.appKey) throw new Error('SynapAgent appKey is required');
  ['allowedScriptOrigins', 'allowedParentOrigins', 'allowedServiceWorkerOrigins'].forEach((key) => {
    if (!Array.isArray(resolved[key])) throw new TypeError(`${key} must be an array`);
    resolved[key] = resolved[key].map(String).slice(0, 20);
  });
  resolved.expectedScriptIntegrity = String(resolved.expectedScriptIntegrity || '').slice(0, 256);
  return resolved;
}

export function createEndpoints(baseUrl) {
  const endpoints = {};
  Object.keys(ENDPOINT_PATHS).forEach((key) => {
    endpoints[key] = baseUrl + ENDPOINT_PATHS[key];
  });
  return endpoints;
}

export function policyCacheKey(appKey) {
  return `__synap_policy_${SDK_VERSION}_${appKey}`;
}
