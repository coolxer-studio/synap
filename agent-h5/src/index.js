import { createH5Codec } from '@coolxer/secure-communication-js';
import httpAjax from './lib/secure-communication/ajax-http';
import baseData from './lib/base-data';
import ctDeviceInfo from './lib/device-info/ct-device-info';
import ctRunningCheck from './lib/running-check/ct-running-check';
import ctIllegalState from './lib/illegal-state/ct-illegal-state';
import ctUserBehavior from './lib/user-behavior/ct-user-behavior';
import agLocalStore from './lib/ag-localstore';
import MessageQueue from './lib/message-queue';
import { decodePolicy } from './lib/policy';
import {
  DEFAULT_OPTIONS,
  SDK_VERSION,
  createEndpoints,
  policyCacheKey,
  resolveOptions
} from './config';

const shared = window.__synapAgentRuntime || {
  status: 'idle',
  options: null,
  endpoints: null,
  codec: null,
  policy: null,
  queue: null,
  initPromise: null,
  inFlight: null,
  retryCount: 0,
  retryTimer: null,
  timers: new Set(),
  idleTasks: new Set(),
  cleanups: [],
  checkStartedAt: 0,
  sendStartedAt: 0,
  cachedDeviceInfo: null,
  warnedLegacy: false,
  metrics: {
    requestFailures: 0,
    parseFailures: 0,
    collectionFailures: 0,
    lastCollectionDurationMs: 0
  },
  generation: 0
};
window.__synapAgentRuntime = shared;

function safeSessionStorage() {
  try {
    const storage = window.sessionStorage;
    const key = '__synap_storage_test';
    storage.setItem(key, '1');
    storage.removeItem(key);
    return storage;
  } catch (_) {
    return null;
  }
}

function sameOptions(left, right) {
  if (!left || !right) return false;
  return left.appKey === right.appKey &&
    left.baseUrl === right.baseUrl &&
    left.requestTimeout === right.requestTimeout &&
    left.queueLimit === right.queueLimit &&
    left.batchSize === right.batchSize &&
    JSON.stringify(left.collectionProfile) === JSON.stringify(right.collectionProfile);
}

function requestHeaders() {
  return {
    'Content-Type': 'application/json;charset=UTF-8',
    code: baseData.getHeaderCode()
  };
}

function scheduleIdle(callback) {
  if (typeof window.requestIdleCallback === 'function') {
    const id = window.requestIdleCallback(() => {
      shared.idleTasks.delete(id);
      callback();
    }, { timeout: 2000 });
    shared.idleTasks.add(id);
    return;
  }
  const id = setTimeout(() => {
    shared.idleTasks.delete(id);
    callback();
  }, 0);
  shared.idleTasks.add(id);
}

function cancelIdleTasks() {
  shared.idleTasks.forEach((id) => {
    if (typeof window.cancelIdleCallback === 'function') window.cancelIdleCallback(id);
    clearTimeout(id);
  });
  shared.idleTasks.clear();
}

function addTimer(callback, interval) {
  const id = setInterval(callback, Math.max(1000, interval));
  shared.timers.add(id);
  return id;
}

function clearRuntimeResources() {
  shared.timers.forEach((id) => clearInterval(id));
  shared.timers.clear();
  if (shared.retryTimer) clearTimeout(shared.retryTimer);
  shared.retryTimer = null;
  cancelIdleTasks();
  shared.cleanups.splice(0).reverse().forEach((cleanup) => {
    try { cleanup(); } catch (_) { }
  });
}

function filteredPolicy(policy, profile) {
  const copy = JSON.parse(JSON.stringify(policy));
  const baseInfo = copy.config.deviceConfig.baseInfo || [];
  const excluded = new Set();
  if (!profile.canvas) ['getCanvasFpCRC', 'getCanvasFpCrc', 'getWebglFp'].forEach((key) => excluded.add(key));
  if (!profile.webgl) ['getWebglVendorAndRenderer', 'getWebglFp'].forEach((key) => excluded.add(key));
  if (!profile.fonts) excluded.add('getFonts');
  copy.config.deviceConfig.baseInfo = baseInfo.filter((key) => !excluded.has(key));
  if (!profile.webrtc && Array.isArray(copy.config.deviceConfig.network)) {
    copy.config.deviceConfig.network = copy.config.deviceConfig.network.filter((key) => key !== 'lanIp');
  }
  return copy;
}

function pushMessage(message) {
  if (!shared.queue) return false;
  const accepted = shared.queue.enqueue(message);
  if (!accepted && shared.queue.dropped === 1) {
    console.warn('SynapAgent message queue is full; subsequent messages may be dropped');
  }
  return accepted;
}

function decodeResponse(response) {
  if (!response) throw new Error('Empty SynapAgent response');
  try {
    return JSON.parse(shared.codec.decodeResponse(response));
  } catch (error) {
    shared.metrics.parseFailures += 1;
    throw new Error(`Invalid SynapAgent response: ${error.message || error}`);
  }
}

function scheduleRetry() {
  if (shared.retryTimer || shared.status === 'destroyed') return;
  const baseDelay = Math.min(30000, 1000 * Math.pow(2, shared.retryCount));
  const delay = Math.round(baseDelay * (0.8 + Math.random() * 0.4));
  shared.retryTimer = setTimeout(() => {
    shared.retryTimer = null;
    flushQueue().catch(() => {});
  }, delay);
}

async function flushQueue() {
  if (shared.status === 'destroyed') return false;
  if (shared.inFlight) return shared.inFlight;
  if (!shared.queue || !shared.codec || !shared.endpoints || shared.queue.items.length === 0) {
    return false;
  }

  const batch = shared.queue.takeBatch(shared.options.batchSize);
  const body = { common: baseData.getCommonInfo(), data: batch };
  let encoded;
  try {
    encoded = shared.codec.encodeRequest(JSON.stringify(body));
  } catch (error) {
    shared.queue.restoreBatch(batch);
    shared.metrics.parseFailures += 1;
    throw error;
  }

  shared.inFlight = httpAjax.post(shared.endpoints.upload, encoded, {
    headers: requestHeaders(),
    timeout: shared.options.requestTimeout
  }).then((response) => {
    shared.retryCount = 0;
    if (response) {
      try { decodeResponse(response); } catch (_) { /* upload responses are advisory */ }
    }
    return true;
  }).catch((error) => {
    shared.queue.restoreBatch(batch);
    shared.metrics.requestFailures += 1;
    shared.retryCount += 1;
    shared.queue.persist();
    scheduleRetry();
    throw error;
  }).finally(() => {
    shared.inFlight = null;
  });
  return shared.inFlight;
}

function collectPeriodicData() {
  if (document.visibilityState === 'hidden') return;
  scheduleIdle(() => {
    const start = Date.now();
    try {
      if (!shared.cachedDeviceInfo) shared.cachedDeviceInfo = ctDeviceInfo.deviceInfo();
      pushMessage(shared.cachedDeviceInfo);
      pushMessage(ctRunningCheck.checkSelfApp());
      if (shared.options.collectionProfile.location) pushMessage(ctRunningCheck.checkLocation());
      pushMessage(ctRunningCheck.checkRuntime());
    } catch (error) {
      shared.metrics.collectionFailures += 1;
      console.warn('SynapAgent collection failed', error);
    } finally {
      shared.metrics.lastCollectionDurationMs = Date.now() - start;
    }
  });
}

function registerLifecyclePersistence() {
  const persist = () => { if (shared.queue) shared.queue.persist(); };
  const visibility = () => {
    if (document.visibilityState === 'hidden') persist();
  };
  window.addEventListener('pagehide', persist);
  document.addEventListener('visibilitychange', visibility);
  shared.cleanups.push(() => window.removeEventListener('pagehide', persist));
  shared.cleanups.push(() => document.removeEventListener('visibilitychange', visibility));
}

function launchProbe(policy) {
  const config = policy.config;
  ctDeviceInfo.loadConfig(config);
  ctRunningCheck.loadConfig(config);
  ctIllegalState.loadConfig(config);
  ctUserBehavior.loadConfig(config);

  registerLifecyclePersistence();
  if (!Number(config.startConfig[0])) return;

  shared.checkStartedAt = Date.now();
  shared.sendStartedAt = Date.now();
  const checkInterval = Number(config.startConfig[2]) * 1000;
  const sendInterval = Number(config.startConfig[4]) * 1000;
  const checkTimer = addTimer(() => {
    if (Date.now() - shared.checkStartedAt > Number(config.startConfig[3]) * 1000) {
      clearInterval(checkTimer);
      shared.timers.delete(checkTimer);
      return;
    }
    collectPeriodicData();
  }, checkInterval);
  const sendTimer = addTimer(() => {
    if (Date.now() - shared.sendStartedAt > Number(config.startConfig[5]) * 1000) {
      clearInterval(sendTimer);
      shared.timers.delete(sendTimer);
      return;
    }
    flushQueue().catch(() => {});
  }, sendInterval);

  if (shared.options.collectionProfile.location) {
    shared.cleanups.push(ctRunningCheck.startLocation());
  }
  if (shared.options.collectionProfile.debug) {
    shared.cleanups.push(ctRunningCheck.debugMonitor(pushMessage));
    shared.cleanups.push(ctUserBehavior.devToolMonitor(pushMessage));
  }
  shared.cleanups.push(ctIllegalState.monitor(pushMessage));
  if (shared.options.collectionProfile.userBehavior) {
    shared.cleanups.push(ctUserBehavior.eventMonitor(pushMessage));
  }
  console.log('synap running:true');
}

function cachedPolicy(appKey) {
  const storage = safeSessionStorage();
  if (!storage) return null;
  const key = policyCacheKey(appKey);
  const encrypted = storage.getItem(key);
  if (!encrypted) return null;
  try {
    return { encrypted, policy: decodePolicy(shared.codec, encrypted) };
  } catch (_) {
    storage.removeItem(key);
    return null;
  }
}

function storePolicy(appKey, encrypted) {
  const storage = safeSessionStorage();
  if (!storage) return;
  try { storage.setItem(policyCacheKey(appKey), encrypted); } catch (_) { }
}

async function fetchPolicy() {
  const body = shared.codec.encodeRequest(JSON.stringify({ common: baseData.getCommonInfo() }));
  const encrypted = await httpAjax.post(shared.endpoints.start, body, {
    headers: requestHeaders(),
    timeout: shared.options.requestTimeout
  });
  const policy = decodePolicy(shared.codec, encrypted);
  storePolicy(shared.options.appKey, encrypted);
  return policy;
}

async function initialize(options) {
  const resolved = resolveOptions(options);
  if ((shared.status === 'running' || shared.status === 'initializing') && sameOptions(shared.options, resolved)) {
    return shared.initPromise || api;
  }
  if (shared.status === 'running' || shared.status === 'initializing') {
    await destroy({ flush: false });
  }

  shared.status = 'initializing';
  const generation = ++shared.generation;
  shared.options = resolved;
  shared.endpoints = createEndpoints(resolved.baseUrl);
  shared.codec = createH5Codec(resolved.appKey);
  shared.cachedDeviceInfo = null;
  window.kk = resolved.appKey;
  window.up = shared.endpoints.upload;
  window.deviceInfoUrl = shared.endpoints.deviceInfo;
  window.labelUrl = shared.endpoints.label;
  window.ratingUrl = shared.endpoints.rating;

  const legacyItems = Array.isArray(window.probeMsgQueue) &&
    (!shared.queue || window.probeMsgQueue !== shared.queue.items)
    ? window.probeMsgQueue.slice()
    : [];
  shared.queue = new MessageQueue({
    storage: safeSessionStorage(),
    storageKey: `__synap_pending_${SDK_VERSION}_${resolved.appKey}`,
    limit: resolved.queueLimit,
    initialItems: legacyItems
  });
  window.probeMsgQueue = shared.queue.items;

  try {
    const cached = cachedPolicy(resolved.appKey);
    const policy = cached ? cached.policy : await fetchPolicy();
    if (generation !== shared.generation) throw new Error('SynapAgent initialization was cancelled');
    shared.policy = filteredPolicy(policy, resolved.collectionProfile);
    window._polic = shared.policy;
    launchProbe(shared.policy);
    shared.status = 'running';
    return api;
  } catch (error) {
    if (generation === shared.generation) shared.status = 'idle';
    clearRuntimeResources();
    throw error;
  }
}

async function init(options = {}) {
  const resolved = resolveOptions(options);
  if (shared.status === 'initializing' && shared.initPromise) {
    if (sameOptions(shared.options, resolved)) return shared.initPromise;
    try { await shared.initPromise; } catch (_) { }
  }
  const promise = initialize(resolved);
  shared.initPromise = promise;
  try {
    return await promise;
  } finally {
    if (shared.initPromise === promise) shared.initPromise = null;
  }
}

async function destroy({ flush = true } = {}) {
  if (shared.status === 'destroyed' || shared.status === 'idle') return;
  shared.status = 'destroying';
  shared.generation += 1;
  if (flush) {
    try { await flushQueue(); } catch (_) { }
  }
  if (shared.queue) shared.queue.persist();
  clearRuntimeResources();
  shared.policy = null;
  shared.cachedDeviceInfo = null;
  shared.status = 'destroyed';
}

async function ensureReady() {
  if (shared.status === 'running') return;
  if (shared.initPromise) await shared.initPromise;
  else await init(window.SynapAgentConfig || {});
}

async function requestInfo(endpoint) {
  await ensureReady();
  const encoded = shared.codec.encodeRequest(JSON.stringify({ common: baseData.getCommonInfo() }));
  const response = await httpAjax.post(shared.endpoints[endpoint], encoded, {
    headers: requestHeaders(),
    timeout: shared.options.requestTimeout
  });
  return decodeResponse(response);
}

function legacyInfo(endpoint) {
  if (!shared.warnedLegacy) {
    shared.warnedLegacy = true;
    console.warn('SynapAgent synchronous query APIs are deprecated; use window.SynapAgent Promise APIs');
  }
  if (shared.status !== 'running') return undefined;
  try {
    const encoded = shared.codec.encodeRequest(JSON.stringify({ common: baseData.getCommonInfo() }));
    const response = httpAjax.requestSync('POST', shared.endpoints[endpoint], {
      headers: requestHeaders(),
      data: encoded
    });
    return decodeResponse(response);
  } catch (error) {
    shared.metrics.requestFailures += 1;
    console.error(error);
    return undefined;
  }
}

function userData(userId, data) {
  if (shared.status !== 'running' || !shared.queue || typeof userId !== 'string' || userId.length === 0 || userId.length > 256 || data == null) return false;
  let serialized;
  try { serialized = JSON.stringify(data); } catch (_) { return false; }
  if (serialized === undefined) return false;
  const bytes = typeof TextEncoder !== 'undefined'
    ? new TextEncoder().encode(serialized).length
    : unescape(encodeURIComponent(serialized)).length;
  if (bytes > shared.options.maxUserDataBytes) return false;
  agLocalStore.set('user_id', userId, 0);
  return pushMessage({
    type: 'UserData',
    buildDate: baseData.currentFormatDate(),
    userId,
    data
  });
}

const api = {
  version: SDK_VERSION,
  init,
  destroy,
  flush: flushQueue,
  userData,
  getDeviceInfo: () => requestInfo('deviceInfo'),
  getRatingInfo: () => requestInfo('rating'),
  getLabelInfo: () => requestInfo('label'),
  getMetrics: () => ({
    ...shared.metrics,
    status: shared.status,
    queue: shared.queue ? shared.queue.metrics() : { queued: 0, dropped: 0 }
  })
};

window.SynapAgent = api;
window.initWithKey = (appKey) => api.init({ ...(window.SynapAgentConfig || {}), appKey });
window.userData = userData;
window.deviceInfo = () => legacyInfo('deviceInfo');
window.ratingInfo = () => legacyInfo('rating');
window.labelInfo = () => legacyInfo('label');

// Preserve the existing zero-configuration integration while allowing hosts to
// define window.SynapAgentConfig before loading the script.
api.init(window.SynapAgentConfig || DEFAULT_OPTIONS).catch((error) => {
  console.warn('SynapAgent failed to initialize', error);
});
