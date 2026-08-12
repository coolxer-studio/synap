'use strict';

const SDK_VERSION = '1.0.0';
const DETECTOR_VERSION = 'wechat-risk-1';
const PLATFORM = 'wechat';
const PROTOCOL_VERSION = 1;
const ENDPOINTS = Object.freeze({});

const BASE_PROFILE = Object.freeze({
  device: true,
  selfApp: true,
  runtime: true,
  integrity: true,
  session: true,
  error: true,
  debug: true,
  behavior: true,
  permission: false,
  localAddress: false,
  location: false,
  network: false
});

const PROFILES = Object.freeze({
  low: BASE_PROFILE,
  balanced: Object.freeze(Object.assign({}, BASE_PROFILE, {
    permission: true,
    localAddress: true
  })),
  full: Object.freeze(Object.keys(BASE_PROFILE).reduce((value, key) => {
    value[key] = true;
    return value;
  }, {})),
  minimal: Object.freeze(Object.keys(BASE_PROFILE).reduce((value, key) => {
    value[key] = false;
    return value;
  }, {}))
});

function trimTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '');
}

function validateBaseUrl(baseUrl, allowInsecureForTesting) {
  if (/^https:\/\//i.test(baseUrl)) return;
  const localHttp = /^http:\/\/(localhost|127\.0\.0\.1|\[::1\])(?::\d+)?$/i.test(baseUrl);
  if (allowInsecureForTesting === true && localHttp) return;
  throw new TypeError('baseUrl must use HTTPS; local HTTP requires allowInsecureForTesting=true');
}

function normalizeOptions(options) {
  const value = options || {};
  const appKey = String(value.appKey || '').trim().toLowerCase();
  if (!/^[0-9a-f]{32}$/.test(appKey)) throw new TypeError('appKey must be a 32-character hexadecimal string');
  const baseUrl = trimTrailingSlash(value.baseUrl);
  validateBaseUrl(baseUrl, value.allowInsecureForTesting);
  const collectionProfile = value.collectionProfile || 'low';
  if (!PROFILES[collectionProfile]) throw new TypeError('collectionProfile must be minimal, low, balanced, or full');
  const appId = Number(value.appId);
  if (!Number.isInteger(appId) || appId <= 0) throw new TypeError('appId must be a positive integer');
  const appName = String(value.appName || '').trim();
  if (!appName || appName.length > 128) throw new TypeError('appName must contain 1-128 characters');
  return Object.freeze({
    appKey,
    appKeyId: value.appKeyId ? String(value.appKeyId) : '',
    baseUrl,
    appId,
    appName,
    collectionProfile,
    capabilities: Object.assign({}, PROFILES[collectionProfile]),
    allowInsecureForTesting: value.allowInsecureForTesting === true,
    timeout: Math.max(1000, Math.min(60000, Number(value.timeout) || 15000)),
    uploadInterval: Math.max(1000, Number(value.uploadInterval) || 10000),
    behaviorInterval: Math.max(5000, Number(value.behaviorInterval) || 10000),
    debugInterval: Math.max(1000, Number(value.debugInterval) || 3000),
    monitorInterval: Math.max(10000, Number(value.monitorInterval) || 60000),
    queueLimit: 50,
    batchLimit: 50
  });
}

function createMeta(options, extra) {
  return Object.assign({
    protocolVersion: PROTOCOL_VERSION,
    appKeyId: options.appKeyId,
    detectorVersion: DETECTOR_VERSION
  }, extra || {});
}

module.exports = {
  SDK_VERSION,
  DETECTOR_VERSION,
  PLATFORM,
  PROTOCOL_VERSION,
  ENDPOINTS,
  PROFILES,
  normalizeOptions,
  createMeta
};
