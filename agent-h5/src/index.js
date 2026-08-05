import { createH5Codec } from '@coolxer/secure-communication-js';
import httpAjax from './lib/secure-communication/ajax-http';
import baseData from './lib/base-data';
import ctDeviceInfo from './lib/device-info/ct-device-info';
import ctRunningCheck from './lib/running-check/ct-running-check';
import ctIllegalState from './lib/illegal-state/ct-illegal-state';
import ctUserBehavior from './lib/user-behavior/ct-user-behavior';
import agLocalStore from './lib/ag-localstore';
import MessageQueue from './lib/message-queue';
import { decodePolicy, validatePolicy } from './lib/policy';
import {
  collectIntegrity, collectResourceIntegrity, networkMonitor, prepareIntegrity,
  resourceReportMonitor, sessionMonitor
} from './lib/security-monitor';
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
  policyFingerprint: '',
  appKeyId: '',
  queue: null,
  initPromise: null,
  inFlight: null,
  retryCount: 0,
  retryTimer: null,
  timers: new Set(),
  idleTasks: new Set(),
  cleanups: [],
  behaviorController: null,
  checkStartedAt: 0,
  sendStartedAt: 0,
  cachedDeviceInfo: null,
  collectionState: {
    deviceSent: false,
    selfAppFingerprint: '',
    runtimeFingerprint: '',
    location: null,
    integrityFingerprint: '',
    resourceFingerprint: ''
  },
  nextSequence: 0,
  collectionStorageKey: '',
  metrics: {
    requestFailures: 0,
    parseFailures: 0,
    batchesSent: 0,
    retries: 0,
    ackFailures: 0,
    serverDuplicates: 0,
    rejectedMessages: 0,
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
    JSON.stringify(left.allowedScriptOrigins) === JSON.stringify(right.allowedScriptOrigins) &&
    JSON.stringify(left.allowedParentOrigins) === JSON.stringify(right.allowedParentOrigins) &&
    JSON.stringify(left.allowedServiceWorkerOrigins) === JSON.stringify(right.allowedServiceWorkerOrigins) &&
    left.expectedScriptIntegrity === right.expectedScriptIntegrity &&
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
  if (!profile.rawEvents && copy.config.eventConfig && Array.isArray(copy.config.eventConfig.event)) {
    copy.config.eventConfig.event = copy.config.eventConfig.event.filter((type) => ['pageshow', 'pagehide'].includes(type));
  }
  return copy;
}

function pushMessage(message) {
  if (!shared.queue) return false;
  const snapshot = withMessageMetadata(message);
  const accepted = shared.queue.enqueue(snapshot);
  if (!accepted && shared.queue.dropped === 1) {
    console.warn('SynapAgent message queue is full; subsequent messages may be dropped');
  }
  return accepted;
}

function createId(prefix) {
  try {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return `${prefix}-${window.crypto.randomUUID()}`;
    }
    if (window.crypto && typeof window.crypto.getRandomValues === 'function') {
      const values = new Uint32Array(4);
      window.crypto.getRandomValues(values);
      return `${prefix}-${Array.from(values).map((value) => value.toString(16)).join('')}`;
    }
  } catch (_) { }
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

async function appKeyIdentifier(appKey) {
  try {
    const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(String(appKey)));
    return Array.from(new Uint8Array(digest).slice(0, 16)).map((value) => value.toString(16).padStart(2, '0')).join('');
  } catch (_) {
    let hash = 2166136261;
    for (const character of String(appKey)) { hash ^= character.charCodeAt(0); hash = Math.imul(hash, 16777619); }
    return `fallback-${(hash >>> 0).toString(16)}`;
  }
}

function withMessageMetadata(message) {
  if (!message || typeof message !== 'object') return message;
  if (message.messageId && Number.isFinite(Number(message.sequence)) && message.observedAt) return message;
  shared.nextSequence += 1;
  return {
    ...message,
    messageId: message.messageId || createId('msg'),
    sequence: Number.isFinite(Number(message.sequence)) ? Number(message.sequence) : shared.nextSequence,
    observedAt: message.observedAt || Date.now()
  };
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
  const batchId = createId('batch');
  const body = {
    common: baseData.getCommonInfo(),
    meta: {
      protocolVersion: 1,
      batchId,
      sessionId: String(baseData.getStartId()),
      attempt: shared.retryCount + 1,
      createdAt: Date.now(),
      detectorVersion: SDK_VERSION,
      appKeyId: shared.appKeyId
    },
    data: batch
  };
  const uploadRequest = async () => {
    const encoded = shared.codec.encodeRequest(JSON.stringify(body));
    const response = await httpAjax.post(shared.endpoints.upload, encoded, {
      headers: requestHeaders(),
      timeout: shared.options.requestTimeout
    });
    return decodeResponse(response);
  };

  shared.inFlight = uploadRequest().then((decoded) => {
    const ack = decoded && decoded.ack;
    const acknowledgedBatchId = ack && (ack.batchId || ack.batch_id);
    if (!decoded || Number(decoded.status) !== 1 || !ack || acknowledgedBatchId !== batchId) {
      const error = new Error('Invalid SynapAgent upload acknowledgement');
      error.type = 'ack';
      throw error;
    }
    const acceptedMessageIds = ack.acceptedMessageIds || ack.accepted_message_ids || [];
    const duplicateMessageIds = ack.duplicateMessageIds || ack.duplicate_message_ids || [];
    const rejectedItems = ack.rejectedItems || ack.rejected_items || [];
    const accepted = new Set([...acceptedMessageIds, ...duplicateMessageIds]);
    const rejected = new Map(rejectedItems.map((item) => [item.messageId || item.message_id, item]));
    const retryMessages = batch.filter((item) => {
      if (accepted.has(item.messageId)) return false;
      const rejection = rejected.get(item.messageId);
      return !rejection || rejection.retryable !== false;
    });
    const permanentlyRejected = batch.length - accepted.size - retryMessages.length;
    shared.queue.completeBatch(retryMessages);
    shared.metrics.serverDuplicates += duplicateMessageIds.length;
    shared.metrics.rejectedMessages += Math.max(0, permanentlyRejected);
    shared.metrics.batchesSent += 1;
    shared.queue.persist();
    if (retryMessages.length > 0) {
      shared.retryCount += 1;
      shared.metrics.retries += 1;
      scheduleRetry();
    } else {
      shared.retryCount = 0;
      if (shared.retryTimer) clearTimeout(shared.retryTimer);
      shared.retryTimer = null;
    }
    return true;
  }).catch((error) => {
    const queuedIds = new Set(shared.queue.items.map((item) => item.messageId));
    const notQueued = batch.filter((item) => !queuedIds.has(item.messageId));
    shared.queue.restoreBatch(notQueued);
    shared.metrics.requestFailures += 1;
    if (error && (error.type === 'ack' || /^Invalid SynapAgent/.test(error.message || ''))) {
      shared.metrics.ackFailures += 1;
    }
    shared.retryCount += 1;
    shared.metrics.retries += 1;
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
      if (!shared.collectionState.deviceSent) {
        if (pushMessage(shared.cachedDeviceInfo)) shared.collectionState.deviceSent = true;
      }

      const selfApp = ctRunningCheck.checkSelfApp();
      const selfAppFingerprint = messageFingerprint(selfApp);
      if (selfAppFingerprint !== shared.collectionState.selfAppFingerprint) {
        if (pushMessage(selfApp)) shared.collectionState.selfAppFingerprint = selfAppFingerprint;
      }

      const runtime = ctRunningCheck.checkRuntime();
      const runtimeFingerprint = messageFingerprint(runtime);
      if (runtimeFingerprint !== shared.collectionState.runtimeFingerprint) {
        if (pushMessage(runtime)) shared.collectionState.runtimeFingerprint = runtimeFingerprint;
      }

      if (shared.options.collectionProfile.location) {
        const location = ctRunningCheck.checkLocation();
        if (hasMeaningfulLocationChange(location, shared.collectionState.location)) {
          if (pushMessage(location)) shared.collectionState.location = locationSnapshot(location);
        }
      }

      if (shared.options.collectionProfile.integrity) {
        const integrity = collectIntegrity(shared.options.appKey, shared.policyFingerprint);
        const integrityFingerprint = messageFingerprint(integrity);
        if (integrityFingerprint !== shared.collectionState.integrityFingerprint) {
          if (pushMessage(integrity)) shared.collectionState.integrityFingerprint = integrityFingerprint;
        }
      }
      if (shared.options.collectionProfile.resourceIntegrity) {
        const resource = collectResourceIntegrity(shared.options);
        const resourceFingerprint = messageFingerprint(resource);
        if (resourceFingerprint !== shared.collectionState.resourceFingerprint) {
          if (pushMessage(resource)) shared.collectionState.resourceFingerprint = resourceFingerprint;
        }
      }
      persistCollectionState();
      shared.queue.persist();
    } catch (error) {
      shared.metrics.collectionFailures += 1;
      console.warn('SynapAgent collection failed', error);
    } finally {
      shared.metrics.lastCollectionDurationMs = Date.now() - start;
    }
  });
}

function loadCollectionState(storageKey) {
  const storage = safeSessionStorage();
  if (!storage) return null;
  try {
    const value = JSON.parse(storage.getItem(storageKey));
    return value && typeof value === 'object' ? value : null;
  } catch (_) { return null; }
}

function persistCollectionState() {
  const storage = safeSessionStorage();
  if (!storage || !shared.collectionStorageKey) return;
  try { storage.setItem(shared.collectionStorageKey, JSON.stringify(shared.collectionState)); }
  catch (_) { }
}

function messageFingerprint(message) {
  const copy = { ...message };
  delete copy.buildDate;
  return JSON.stringify(copy);
}

function locationSnapshot(message) {
  const keys = (window.ct_location_config && window.ct_location_config.base) || [];
  const values = Array.isArray(message && message.base) ? message.base : [];
  const value = (key) => Number(values[keys.indexOf(key)]) || 0;
  return {
    timestamp: value('timestamp') || value('time'),
    latitude: value('latitude'),
    longitude: value('longitude'),
    accuracy: value('accuracy')
  };
}

function hasMeaningfulLocationChange(message, previous) {
  const current = locationSnapshot(message);
  if (!current.latitude && !current.longitude) return false;
  if (!previous) return true;
  const latitudeDistance = (current.latitude - previous.latitude) * 111000;
  const longitudeDistance = (current.longitude - previous.longitude) * 111000 * Math.cos(current.latitude * Math.PI / 180);
  const distance = Math.sqrt(latitudeDistance * latitudeDistance + longitudeDistance * longitudeDistance);
  const accuracyChanged = Math.abs(current.accuracy - previous.accuracy) >= 50;
  return distance >= 100 || accuracyChanged || current.timestamp - previous.timestamp >= 60000;
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
  ctDeviceInfo.loadConfig(config, shared.options.collectionProfile);
  ctRunningCheck.loadConfig(config);
  ctIllegalState.loadConfig(config);
  ctUserBehavior.loadConfig(config);

  registerLifecyclePersistence();
  if (!Number(config.startConfig[0])) return;

  collectPeriodicData();

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
    shared.behaviorController = ctUserBehavior.behaviorMonitor(pushMessage);
    shared.cleanups.push(shared.behaviorController);
  }
  if (shared.options.collectionProfile.session) {
    shared.cleanups.push(sessionMonitor(pushMessage, shared.options.appKey));
  }
  if (shared.options.collectionProfile.network) {
    shared.cleanups.push(networkMonitor(pushMessage, Object.values(shared.endpoints)));
  }
  if (shared.options.collectionProfile.securityReports) {
    shared.cleanups.push(resourceReportMonitor(pushMessage));
  }
  if (shared.options.collectionProfile.integrity || shared.options.collectionProfile.resourceIntegrity) {
    addTimer(collectPeriodicData, 60000);
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
    if (encrypted.startsWith('json:')) return { encrypted, policy: validatePolicy(JSON.parse(encrypted.slice(5))) };
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
  const body = shared.codec.encodeRequest(JSON.stringify({
    common: baseData.getCommonInfo(),
    meta: { protocolVersion: 1, appKeyId: shared.appKeyId, detectorVersion: SDK_VERSION }
  }));
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
  shared.appKeyId = await appKeyIdentifier(resolved.appKey);
  shared.cachedDeviceInfo = null;
  shared.behaviorController = null;
  shared.collectionStorageKey = `__synap_collection_${SDK_VERSION}_${resolved.appKey}_${baseData.getStartId()}`;
  shared.collectionState = loadCollectionState(shared.collectionStorageKey) || {
    deviceSent: false,
    selfAppFingerprint: '',
    runtimeFingerprint: '',
    location: null,
    integrityFingerprint: '',
    resourceFingerprint: ''
  };
  window.kk = resolved.appKey;
  window.up = shared.endpoints.upload;
  window.deviceInfoUrl = shared.endpoints.deviceInfo;
  window.labelUrl = shared.endpoints.label;
  window.ratingUrl = shared.endpoints.rating;

  // Common data is used by the policy request itself, so finish the bounded
  // local-address probe before the first request and reuse its cached result.
  await baseData.prepareDeviceInfo({
    collectionProfile: resolved.collectionProfile,
    timeout: Math.min(1500, Math.max(250, resolved.requestTimeout / 5))
  });
  await prepareIntegrity(resolved.collectionProfile,
    Math.min(1500, Math.max(250, resolved.requestTimeout / 5)));
  if (generation !== shared.generation) throw new Error('SynapAgent initialization was cancelled');

  const existingItems = Array.isArray(window.probeMsgQueue) &&
    (!shared.queue || window.probeMsgQueue !== shared.queue.items)
    ? window.probeMsgQueue.slice()
    : [];
  shared.queue = new MessageQueue({
    storage: safeSessionStorage(),
    storageKey: `__synap_pending_${SDK_VERSION}_${resolved.appKey}`,
    limit: resolved.queueLimit,
    initialItems: existingItems
  });
  shared.nextSequence = shared.queue.items.reduce((max, item) => Math.max(max, Number(item.sequence) || 0), 0);
  const normalizedPending = shared.queue.items.map((item) => withMessageMetadata(item));
  shared.queue.items.splice(0, shared.queue.items.length, ...normalizedPending);
  window.probeMsgQueue = shared.queue.items;

  try {
    let policy;
    try {
      policy = await fetchPolicy();
    } catch (error) {
      const cached = cachedPolicy(resolved.appKey);
      if (!cached) throw error;
      policy = cached.policy;
    }
    if (generation !== shared.generation) throw new Error('SynapAgent initialization was cancelled');
    shared.policy = filteredPolicy(policy, resolved.collectionProfile);
    shared.policyFingerprint = JSON.stringify(shared.policy);
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
  clearRuntimeResources();
  if (shared.queue) shared.queue.persist();
  shared.policy = null;
  shared.policyFingerprint = '';
  shared.appKeyId = '';
  shared.behaviorController = null;
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
  const encoded = shared.codec.encodeRequest(JSON.stringify({
    common: baseData.getCommonInfo(),
    meta: { protocolVersion: 1, appKeyId: shared.appKeyId, detectorVersion: SDK_VERSION }
  }));
  const response = await httpAjax.post(shared.endpoints[endpoint], encoded, {
    headers: requestHeaders(),
    timeout: shared.options.requestTimeout
  });
  return decodeResponse(response);
}

function userData(userId, data) {
  if (shared.status !== 'running' || !shared.queue || typeof userId !== 'string' || userId.length === 0 || userId.length > 256 || data == null) return false;
  const sensitiveKey = /(password|passwd|pwd|token|authorization|cookie|secret|id.?card|credit.?card|card.?number|phone|email)/i;
  const containsSensitiveKey = (value, depth = 0) => {
    if (!value || typeof value !== 'object' || depth > 6) return false;
    if (Array.isArray(value)) return value.some((item) => containsSensitiveKey(item, depth + 1));
    return Object.keys(value).some((key) => sensitiveKey.test(key) || containsSensitiveKey(value[key], depth + 1));
  };
  if (containsSensitiveKey(data)) return false;
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

function markAction(action) {
  if (shared.status !== 'running' || !shared.behaviorController || typeof shared.behaviorController.markAction !== 'function') return false;
  return shared.behaviorController.markAction(action);
}

const api = {
  version: SDK_VERSION,
  init,
  destroy,
  flush: flushQueue,
  userData,
  markAction,
  getDeviceInfo: () => requestInfo('deviceInfo'),
  getRatingInfo: () => requestInfo('rating'),
  getLabelInfo: () => requestInfo('label'),
  getMetrics: () => ({
    ...shared.metrics,
    status: shared.status,
    transportTrust: 'app-key-codec/untrusted',
    networkInfo: baseData.getNetworkInfo(),
    queue: shared.queue ? shared.queue.metrics() : { queued: 0, inFlight: 0, dropped: 0 }
  })
};

window.SynapAgent = api;
window.initWithKey = (appKey) => api.init({ ...(window.SynapAgentConfig || {}), appKey });
window.userData = userData;

// Preserve the existing zero-configuration integration while allowing hosts to
// define window.SynapAgentConfig before loading the script.
api.init(window.SynapAgentConfig || DEFAULT_OPTIONS).catch((error) => {
  console.warn('SynapAgent failed to initialize', error);
});
