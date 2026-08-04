export const PLATFORM = 'h5';
export const APP_ID = 1;
export const SDK_VERSION = '1.2.0';
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
  canvas: true,
  webgl: true,
  fonts: true,
  webrtc: true,
  location: true,
  userBehavior: true,
  debug: true,
  integrity: true,
  session: true,
  network: false
});

export const DEFAULT_OPTIONS = Object.freeze({
  appKey: DEFAULT_APP_KEY,
  baseUrl: DEFAULT_BASE_URL,
  collectionProfile: DEFAULT_COLLECTION_PROFILE,
  requestTimeout: 15000,
  queueLimit: 50,
  batchSize: 50,
  maxUserDataBytes: 32768
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
  if (!profile || profile === 'full') {
    return { ...DEFAULT_COLLECTION_PROFILE };
  }
  if (profile === 'minimal') {
    return {
      canvas: false,
      webgl: false,
      fonts: false,
      webrtc: false,
      location: false,
      userBehavior: false,
      debug: false,
      integrity: false,
      session: false,
      network: false
    };
  }
  if (typeof profile !== 'object') {
    throw new TypeError('collectionProfile must be "full", "minimal", or an object');
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
