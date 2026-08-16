import {
  IndexedDbIdentityStore,
  SecureClientConfig,
  SecureRequest,
  createSecureClient
} from '@coolxer/secure-communication-js';
import baseData from './lib/base-data';
import ctDeviceInfo from './lib/device-info/ct-device-info';
import { applyCollectionProfile } from './lib/device-info/collection-policy';
import ctRunningCheck from './lib/running-check/ct-running-check';
import ctIllegalState from './lib/illegal-state/ct-illegal-state';
import ctUserBehavior from './lib/user-behavior/ct-user-behavior';
import agLocalStore from './lib/ag-localstore';
import MessageQueue from './lib/message-queue';
import { validatePolicy } from './lib/policy';
import {
  collectIntegrity, collectResourceIntegrity, networkMonitor, prepareIntegrity,
  resourceReportMonitor, sessionMonitor
} from './lib/security-monitor';
import {
  DEFAULT_OPTIONS,
  SDK_VERSION,
  createEndpoints,
  hasSecurityBootstrap,
  isLoopbackHttpUrl,
  policyCacheKey,
  resolveOptions
} from './config';

const shared = {
  status: 'idle',
  options: null,
  endpoints: null,
  secureClient: null,
  policy: null,
  policyFingerprint: '',
  appKeyId: '',
  queue: null,
  initPromise: null,
  inFlight: null,
  retryCount: 0,
  retryTimer: null,
  startRetryTimer: null,
  startRetryCount: 0,
  startTracking: null,
  startAcknowledged: false,
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

const SECURE_IDENTITY_DATABASE = 'coolxer-secure-communication-v2';

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
  return left.appId === right.appId &&
    left.baseUrl === right.baseUrl &&
    left.requestTimeout === right.requestTimeout &&
    left.queueLimit === right.queueLimit &&
    left.batchSize === right.batchSize &&
    JSON.stringify(left.allowedScriptOrigins) === JSON.stringify(right.allowedScriptOrigins) &&
    JSON.stringify(left.allowedParentOrigins) === JSON.stringify(right.allowedParentOrigins) &&
    JSON.stringify(left.allowedServiceWorkerOrigins) === JSON.stringify(right.allowedServiceWorkerOrigins) &&
    left.expectedScriptIntegrity === right.expectedScriptIntegrity &&
    JSON.stringify(left.collectionProfile) === JSON.stringify(right.collectionProfile) &&
    JSON.stringify(left.serverTrustAnchors) === JSON.stringify(right.serverTrustAnchors);
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
  if (shared.startRetryTimer) clearTimeout(shared.startRetryTimer);
  shared.startRetryTimer = null;
  cancelIdleTasks();
  shared.cleanups.splice(0).reverse().forEach((cleanup) => {
    try { cleanup(); } catch (_) { }
  });
}

function filteredPolicy(policy, profile) {
  return applyCollectionProfile(policy, profile);
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

function startTrackingStorageKey() {
  return `__synap_start_${SDK_VERSION}_${shared.appKeyId}_${baseData.getStartId()}`;
}

function loadOrCreateStartTracking() {
  const storage = safeSessionStorage();
  const storageKey = startTrackingStorageKey();
  if (storage) {
    try {
      const existing = JSON.parse(storage.getItem(storageKey));
      if (existing && existing.messageId && existing.batchId
          && existing.sessionId === String(baseData.getStartId()) && Number(existing.sequence) === 1) {
        return existing;
      }
    } catch (_) { }
  }
  const now = Date.now();
  const tracking = {
    messageId: createId('start-msg'),
    batchId: createId('start-batch'),
    sessionId: String(baseData.getStartId()),
    attempt: 0,
    createdAt: now,
    observedAt: now,
    sequence: 1,
    acknowledged: false
  };
  if (storage) {
    try { storage.setItem(storageKey, JSON.stringify(tracking)); } catch (_) { }
  }
  return tracking;
}

function persistStartTracking() {
  const storage = safeSessionStorage();
  if (!storage || !shared.startTracking) return;
  try { storage.setItem(startTrackingStorageKey(), JSON.stringify(shared.startTracking)); } catch (_) { }
}

function nextStartMeta() {
  shared.startTracking.attempt = Math.max(0, Number(shared.startTracking.attempt) || 0) + 1;
  persistStartTracking();
  const { acknowledged, ...meta } = shared.startTracking;
  return meta;
}

function acknowledgeStart() {
  shared.startAcknowledged = true;
  shared.startTracking.acknowledged = true;
  shared.startRetryCount = 0;
  persistStartTracking();
}

function scheduleStartRetry() {
  if (shared.startAcknowledged || shared.startRetryTimer || shared.status !== 'running') return;
  const delay = Math.min(30000, 1000 * Math.pow(2, shared.startRetryCount));
  shared.startRetryTimer = setTimeout(() => {
    shared.startRetryTimer = null;
    fetchPolicy().then(() => {
      flushQueue().catch(() => {});
    }).catch(() => {
      shared.startRetryCount += 1;
      scheduleStartRetry();
    });
  }, Math.round(delay * (0.8 + Math.random() * 0.4)));
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
    return typeof response === 'string' ? JSON.parse(response) : response;
  } catch (error) {
    shared.metrics.parseFailures += 1;
    throw new Error(`Invalid SynapAgent response: ${error.message || error}`);
  }
}

async function securePost(path, body, requestId) {
  if (!shared.secureClient) throw new Error('SynapAgent secure client is unavailable');
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), shared.options.requestTimeout) : null;
  try {
    const response = await shared.secureClient.request(new SecureRequest({
      method: 'POST',
      logicalPath: path,
      contentType: 'application/json',
      protectedHeaders: { code: baseData.getHeaderCode() },
      body: JSON.stringify(body),
      requestId: requestId || createId('request')
    }), { signal: controller ? controller.signal : undefined });
    return decodeResponse(response.text());
  } finally {
    if (timer) clearTimeout(timer);
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
  if (!shared.startAcknowledged) {
    scheduleStartRetry();
    return false;
  }
  if (!shared.queue || !shared.secureClient || !shared.endpoints || shared.queue.items.length === 0) {
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
    return securePost(shared.endpoints.upload, body, batchId);
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
        const integrity = collectIntegrity(shared.options.appId, shared.policyFingerprint);
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
    shared.cleanups.push(sessionMonitor(pushMessage, shared.options.appId));
  }
  if (shared.options.collectionProfile.network) {
    shared.cleanups.push(networkMonitor(pushMessage, [`${shared.options.baseUrl}/sc/v1/`]));
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
    return null;
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
  const body = {
    common: baseData.getCommonInfo(),
    meta: {
      protocolVersion: 1,
      appKeyId: shared.appKeyId,
      detectorVersion: SDK_VERSION,
      ...nextStartMeta()
    }
  };
  const policy = validatePolicy(await securePost(shared.endpoints.start, body, createId('start')));
  acknowledgeStart();
  storePolicy(shared.options.appId, `json:${JSON.stringify(policy)}`);
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
  shared.appKeyId = resolved.appId;
  shared.startTracking = loadOrCreateStartTracking();
  shared.startAcknowledged = shared.startTracking.acknowledged === true;
  shared.startRetryCount = 0;
  shared.secureClient = createSecureClient(new SecureClientConfig({
    baseUrl: resolved.baseUrl,
    appId: shared.appKeyId,
    deviceType: 'H5',
    serverTrustAnchors: resolved.serverTrustAnchors,
    identityStore: new IndexedDbIdentityStore({ databaseName: SECURE_IDENTITY_DATABASE }),
    requestTimeoutMillis: resolved.requestTimeout,
    allowInsecureLoopbackForTesting: isLoopbackHttpUrl(resolved.baseUrl),
    // SecureClientConfig stores the supplied function and invokes it through
    // the config object. Bind the browser native to avoid an illegal receiver.
    fetch: window.fetch.bind(window)
  }));
  shared.cachedDeviceInfo = null;
  shared.behaviorController = null;
  shared.collectionStorageKey = `__synap_collection_${SDK_VERSION}_${resolved.appId}_${baseData.getStartId()}`;
  shared.collectionState = loadCollectionState(shared.collectionStorageKey) || {
    deviceSent: false,
    selfAppFingerprint: '',
    runtimeFingerprint: '',
    location: null,
    integrityFingerprint: '',
    resourceFingerprint: ''
  };
  // Common data is used by the policy request itself, so finish the bounded
  // local-address probe before the first request and reuse its cached result.
  await baseData.prepareDeviceInfo({
    collectionProfile: resolved.collectionProfile,
    timeout: Math.min(1500, Math.max(250, resolved.requestTimeout / 5))
  });
  await prepareIntegrity(resolved.collectionProfile,
    Math.min(1500, Math.max(250, resolved.requestTimeout / 5)));
  if (generation !== shared.generation) throw new Error('SynapAgent initialization was cancelled');

  const existingItems = [];
  shared.queue = new MessageQueue({
    storage: safeSessionStorage(),
    storageKey: `__synap_pending_${SDK_VERSION}_${resolved.appId}`,
    limit: resolved.queueLimit,
    initialItems: existingItems
  });
  shared.nextSequence = shared.queue.items.reduce((max, item) => Math.max(max, Number(item.sequence) || 0), 1);
  const normalizedPending = shared.queue.items.map((item) => withMessageMetadata(item));
  shared.queue.items.splice(0, shared.queue.items.length, ...normalizedPending);

  try {
    let policy;
    try {
      policy = await fetchPolicy();
    } catch (error) {
      const cached = cachedPolicy(resolved.appId);
      if (!cached) throw error;
      policy = cached.policy;
    }
    if (generation !== shared.generation) throw new Error('SynapAgent initialization was cancelled');
    shared.policy = filteredPolicy(policy, resolved.collectionProfile);
    shared.policyFingerprint = JSON.stringify(shared.policy);
    launchProbe(shared.policy);
    shared.status = 'running';
    if (!shared.startAcknowledged) scheduleStartRetry();
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
  } catch (error) {
    if (shared.status === 'initializing') shared.status = 'idle';
    if (shared.secureClient) shared.secureClient.closeSession();
    shared.secureClient = null;
    clearRuntimeResources();
    throw error;
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
  shared.startTracking = null;
  shared.startAcknowledged = false;
  if (shared.secureClient) shared.secureClient.closeSession();
  shared.secureClient = null;
  shared.status = 'destroyed';
}

async function ensureReady() {
  if (shared.status === 'running') return;
  if (shared.initPromise) await shared.initPromise;
  else await init(window.SynapAgentConfig || {});
}

async function requestInfo(endpoint) {
  await ensureReady();
  return securePost(shared.endpoints[endpoint], {
    common: baseData.getCommonInfo(),
    meta: { protocolVersion: 1, appKeyId: shared.appKeyId, detectorVersion: SDK_VERSION }
  }, createId(endpoint));
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
    transportTrust: 'sc1-authenticated/P256_HKDF_SHA256_AES256_GCM',
    networkInfo: baseData.getNetworkInfo(),
    queue: shared.queue ? shared.queue.metrics() : { queued: 0, inFlight: 0, dropped: 0 }
  })
};

window.SynapAgent = api;

// synap-build embeds the public appId and trust anchor into DEFAULT_OPTIONS.
// A directly built SDK receives the same public values from the host page. If
// they are not ready yet, expose the API and let the host call init() later.
const bootstrapOptions = window.SynapAgentConfig || DEFAULT_OPTIONS;
if (hasSecurityBootstrap(bootstrapOptions)) {
  api.init(bootstrapOptions).catch((error) => {
    console.warn('SynapAgent failed to initialize', error);
  });
}
