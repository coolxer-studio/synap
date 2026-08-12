'use strict';

const { SDK_VERSION, DETECTOR_VERSION, ENDPOINTS, normalizeOptions, createMeta } = require('./config');
const { hashText, safeClone, sanitizeText, sanitizeData, routeTemplate } = require('./privacy');
const { uuid } = require('./id');
const { WxStorage } = require('./storage');
const { MessageQueue } = require('./message-queue');
const { WxTransport } = require('./transport');
const { DeviceCollector } = require('./device');
const {
  ListenerRegistry,
  ErrorMonitor,
  BehaviorMonitor,
  IntegrityMonitor,
  NetworkMonitor,
  runtimeFact
} = require('./detectors');

const DAY = 24 * 60 * 60 * 1000;

function disabledLegacyCodec() {
  throw new Error('Legacy Mini Program codec is disabled for security protocol v1');
}

function policyFingerprint() {
  return hashText(JSON.stringify(state.policy || {}));
}
const state = {
  status: 'idle',
  generation: 0,
  initPromise: null,
  options: null,
  signature: '',
  wx: null,
  codec: null,
  storage: null,
  transport: null,
  queue: null,
  device: null,
  policy: null,
  sessionId: '',
  sequence: 0,
  userId: '',
  uploadPromise: null,
  retryCount: 0,
  retryTimer: null,
  startRetryTimer: null,
  startRetryCount: 0,
  startTracking: null,
  startAcknowledged: false,
  foreground: true,
  timers: new Set(),
  listeners: null,
  errors: null,
  behavior: null,
  integrity: null,
  network: null,
  session: null,
  metrics: null
};

function freshMetrics() {
  return {
    batchesSent: 0,
    messagesAccepted: 0,
    retries: 0,
    ackFailures: 0,
    requestFailures: 0,
    serverDuplicates: 0,
    rejectedMessages: 0,
    droppedMessages: 0,
    expiredMessages: 0,
    restoredMessages: 0,
    facts: {},
    errors: {}
  };
}

function setTimer(callback, delay, repeating) {
  let handle;
  if (repeating) handle = setInterval(callback, delay);
  else handle = setTimeout(() => {
    state.timers.delete(handle);
    callback();
  }, delay);
  state.timers.add(handle);
  return handle;
}

function clearTimers() {
  state.timers.forEach((timer) => {
    clearTimeout(timer);
    clearInterval(timer);
  });
  state.timers.clear();
  state.retryTimer = null;
  state.startRetryTimer = null;
}

function normalizeInitializedOptions(options) {
  const normalized = normalizeOptions(options);
  const appKeyId = normalized.appKeyId || hashText(normalized.appKey);
  return Object.freeze(Object.assign({}, normalized, { appKeyId }));
}

function optionsSignature(options) {
  return JSON.stringify([options.appKey, options.baseUrl, options.appId, options.appName, options.collectionProfile]);
}

function policyKey() {
  const mini = state.device && state.device.miniProgramInfo();
  return `__synap_policy_${SDK_VERSION}_${state.options.appKeyId}_${mini && mini.appId || 'unknown'}`;
}

function startTrackingKey() {
  return `__synap_start_${SDK_VERSION}_${state.options.appKeyId}_${state.sessionId}`;
}

function loadOrCreateStartTracking() {
  const existing = state.storage.get(startTrackingKey());
  if (existing && existing.messageId && existing.batchId
      && existing.sessionId === state.sessionId && Number(existing.sequence) === 1) return existing;
  const now = Date.now();
  const tracking = {
    messageId: `start-msg-${uuid(state.wx)}`,
    batchId: `start-batch-${uuid(state.wx)}`,
    sessionId: state.sessionId,
    attempt: 0,
    createdAt: now,
    observedAt: now,
    sequence: 1,
    acknowledged: false
  };
  state.storage.set(startTrackingKey(), tracking);
  return tracking;
}

function nextStartMeta() {
  state.startTracking.attempt = Math.max(0, Number(state.startTracking.attempt) || 0) + 1;
  state.storage.set(startTrackingKey(), state.startTracking);
  const meta = Object.assign({}, state.startTracking);
  delete meta.acknowledged;
  return meta;
}

function scheduleStartRetry() {
  if (state.startAcknowledged || state.startRetryTimer || state.status !== 'running') return;
  const delay = Math.min(60000, 1000 * Math.pow(2, Math.min(6, state.startRetryCount)));
  state.startRetryTimer = setTimer(() => {
    state.startRetryTimer = null;
    requestPolicy().then(() => flush().catch(() => {})).catch(() => {
      state.startRetryCount += 1;
      scheduleStartRetry();
    });
  }, Math.round(delay * (0.8 + Math.random() * 0.4)), false);
}

function validatePolicy(value) {
  if (!value || typeof value !== 'object' || !Array.isArray(value.startConfig) || value.startConfig.length < 5) {
    throw new TypeError('invalid WeChat checker policy');
  }
  const requiredDeviceSections = ['device', 'screen', 'systemSetting', 'appBaseInfo', 'appAuthorizeSetting', 'battery', 'uuid'];
  if (!value.deviceConfig || requiredDeviceSections.some((name) => !Array.isArray(value.deviceConfig[name]))) {
    throw new TypeError('invalid WeChat device policy');
  }
  const start = value.startConfig.map(Number);
  if (!start.every(Number.isFinite) || (start[0] !== 0 && start[0] !== 1)) throw new TypeError('invalid WeChat startConfig');
  return Object.assign({}, value, { startConfig: start });
}

function readPolicyCache() {
  const encrypted = state.storage.get(policyKey());
  if (!encrypted) return null;
  try {
    const parsed = JSON.parse(state.codec.decrypt(encrypted));
    if (!parsed.cachedAt || Date.now() - parsed.cachedAt > DAY) throw new Error('expired policy');
    return validatePolicy(parsed.policy);
  } catch (_) {
    state.storage.remove(policyKey());
    return null;
  }
}

function writePolicyCache(policy) {
  try {
    state.storage.set(policyKey(), state.codec.encrypt(JSON.stringify({ cachedAt: Date.now(), policy })));
  } catch (_) { /* cache is optional */ }
}

async function requestPolicy() {
  const body = {
    common: state.device.common(state.userId),
    meta: createMeta(state.options, nextStartMeta())
  };
  const response = await state.transport.post(ENDPOINTS.start, body, state.device.headerCode());
  const policy = validatePolicy(response.config);
  state.startAcknowledged = true;
  state.startTracking.acknowledged = true;
  state.startRetryCount = 0;
  state.storage.set(startTrackingKey(), state.startTracking);
  writePolicyCache(policy);
  return policy;
}

function createMessage(fact) {
  state.sequence += 1;
  return Object.assign({}, fact, {
    messageId: `msg-${uuid(state.wx)}`,
    sequence: state.sequence,
    observedAt: Date.now()
  });
}

function enqueue(fact) {
  if (!state.queue || !fact || typeof fact.type !== 'string') return false;
  const message = createMessage(fact);
  const queued = state.queue.enqueue({
    message,
    common: state.device.common(state.userId),
    headerCode: state.device.headerCode(),
    sessionId: state.sessionId
  });
  if (!queued) return false;
  state.metrics.facts[fact.type] = (state.metrics.facts[fact.type] || 0) + 1;
  state.queue.persist(Date.now());
  return true;
}

function scheduleRetry() {
  if (state.retryTimer || state.status !== 'running') return;
  const base = Math.min(60000, 1000 * Math.pow(2, Math.min(6, state.retryCount)));
  const delay = Math.round(base * (0.8 + Math.random() * 0.4));
  state.retryTimer = setTimer(() => {
    state.retryTimer = null;
    flush().catch(() => {});
  }, delay, false);
}

async function flush() {
  if (!state.queue || !state.transport || state.status === 'destroyed') return false;
  if (!state.startAcknowledged) {
    scheduleStartRetry();
    return false;
  }
  if (state.uploadPromise) return state.uploadPromise;
  const batch = state.queue.takeBatch();
  if (!batch) return false;
  const batchId = `batch-${uuid(state.wx)}`;
  const first = batch[0];
  const body = {
    common: first.common,
    meta: createMeta(state.options, {
      batchId,
      sessionId: first.sessionId,
      attempt: state.retryCount + 1,
      createdAt: Date.now()
    }),
    data: batch.map((entry) => entry.message)
  };
  state.uploadPromise = state.transport.post(ENDPOINTS.upload, body, first.headerCode).then((response) => {
    const ack = response.ack;
    if (!ack || ack.batchId !== batchId || !Array.isArray(ack.acceptedMessageIds)
        || !Array.isArray(ack.duplicateMessageIds) || !Array.isArray(ack.rejectedItems)) {
      const error = new Error('invalid upload acknowledgement');
      error.type = 'ack';
      throw error;
    }
    const result = state.queue.complete(ack.acceptedMessageIds, ack.duplicateMessageIds, ack.rejectedItems);
    state.metrics.batchesSent += 1;
    state.metrics.messagesAccepted += result.confirmed;
    state.metrics.serverDuplicates += ack.duplicateMessageIds.length;
    state.metrics.rejectedMessages += result.rejected;
    state.queue.persist(Date.now());
    if (result.restored) {
      state.retryCount += 1;
      state.metrics.retries += 1;
      scheduleRetry();
    } else {
      state.retryCount = 0;
    }
    return true;
  }).catch((error) => {
    if (error && error.retryable === false) {
      const result = state.queue.complete([], [], batch.map((entry) => ({
        messageId: entry.message.messageId,
        retryable: false
      })));
      state.metrics.requestFailures += 1;
      state.metrics.rejectedMessages += result.rejected;
      state.queue.persist(Date.now());
      return false;
    }
    state.queue.restoreInFlight();
    state.queue.persist(Date.now());
    state.metrics.requestFailures += 1;
    if (error && error.type === 'ack') state.metrics.ackFailures += 1;
    state.retryCount += 1;
    state.metrics.retries += 1;
    scheduleRetry();
    throw error;
  }).finally(() => {
    state.uploadPromise = null;
  });
  return state.uploadPromise;
}

function sessionFact(reason) {
  const now = Date.now();
  const session = state.session;
  const wallDelta = now - session.lastWall;
  const monotonic = typeof performance !== 'undefined' && performance.now ? performance.now() : now;
  const monoDelta = monotonic - session.lastMono;
  if (wallDelta + 1000 < monoDelta) session.clockRollback = true;
  session.lastWall = now;
  session.lastMono = monotonic;
  const route = routeTemplate();
  if (route !== session.route) {
    session.routeChanges += 1;
    session.route = route;
  }
  return {
    type: 'SessionData',
    buildDate: new Date().toISOString(),
    data: {
      reason,
      foreground: state.foreground,
      showCount: session.showCount,
      hideCount: session.hideCount,
      routeChanges: session.routeChanges,
      networkChanges: session.networkChanges,
      memoryWarnings: session.memoryWarnings,
      clockRollback: session.clockRollback,
      sessionDuration: now - session.startedAt,
      route
    }
  };
}

function installLifecycle() {
  const listeners = state.listeners;
  listeners.on('onAppHide', 'offAppHide', () => {
    state.foreground = false;
    state.session.hideCount += 1;
    if (state.behavior) state.behavior.flush();
    enqueue(sessionFact('hide'));
    state.queue.persist(Date.now());
    flush().catch(() => {});
  });
  listeners.on('onAppShow', 'offAppShow', () => {
    state.foreground = true;
    state.session.showCount += 1;
    if (state.behavior) state.behavior.reset();
    enqueue(sessionFact('show'));
  });
  listeners.on('onNetworkStatusChange', 'offNetworkStatusChange', (event) => {
    state.session.networkChanges += 1;
    state.device.network = { state: 'available', value: { networkType: event && event.networkType || 'unknown' } };
  });
  listeners.on('onMemoryWarning', 'offMemoryWarning', (event) => {
    state.session.memoryWarnings += 1;
    enqueue({ type: 'SessionData', buildDate: new Date().toISOString(), data: {
      reason: 'memory_warning', level: Number(event && event.level) || 0,
      memoryWarnings: state.session.memoryWarnings
    } });
  });
  if (state.options.capabilities.error) {
    listeners.on('onError', 'offError', (message) => state.errors.capture('app_error', message));
    listeners.on('onUnhandledRejection', 'offUnhandledRejection', (event) => state.errors.capture('unhandled_rejection', event));
    listeners.on('onPageNotFound', 'offPageNotFound', (event) => state.errors.capture('page_not_found', event, event && event.path));
  }
}

function startCollectors() {
  if (state.policy.startConfig[0] !== 1) return;
  const caps = state.options.capabilities;
  if (caps.device) enqueue(state.device.deviceFact());
  if (caps.selfApp) enqueue(state.device.selfAppFact());
  if (caps.runtime) enqueue(runtimeFact(state.device));
  if (caps.integrity) enqueue(state.integrity.collect(state.options, policyFingerprint()));
  if (caps.session) enqueue(sessionFact('init'));
  if (caps.location) {
    const location = state.device.locationFact();
    if (location) enqueue(location);
  }
  if (caps.behavior) {
    setTimer(() => { if (state.foreground) state.behavior.flush(); }, state.options.behaviorInterval, true);
  }
  setTimer(() => {
    if (!state.foreground) return;
    if (caps.runtime) enqueue(runtimeFact(state.device));
    if (caps.integrity) enqueue(state.integrity.collect(state.options, policyFingerprint()));
    if (caps.session) enqueue(sessionFact('periodic'));
    if (caps.network && state.network) {
      const network = state.network.flush();
      if (network) enqueue(network);
    }
  }, state.options.monitorInterval, true);
  setTimer(() => { if (state.foreground) flush().catch(() => {}); }, state.options.uploadInterval, true);
  if (caps.debug) {
    let expected = Date.now() + state.options.debugInterval;
    let lastDetected = 0;
    setTimer(() => {
      const now = Date.now();
      const pause = now - expected;
      expected = now + state.options.debugInterval;
      if (state.foreground && pause > Math.max(1500, state.options.debugInterval) && now - lastDetected > 30000) {
        lastDetected = now;
        enqueue({ type: 'DebugData', buildDate: new Date().toISOString(), data: {
          longPause: pause,
          foreground: true,
          observation: true
        } });
      }
    }, state.options.debugInterval, true);
  }
}

/* eslint-disable no-unreachable -- retained migration implementation is intentionally gated */
async function initialize(rawOptions) {
  throw new Error('Synap security protocol v1 does not support WeChat Mini Program');
  const options = normalizeInitializedOptions(rawOptions);
  const signature = optionsSignature(options);
  if (state.status === 'running' && state.signature === signature) return api;
  if (state.status === 'running' || state.status === 'initializing') await destroy({ flush: false });
  if (typeof wx === 'undefined' || typeof wx.request !== 'function') throw new Error('WeChat Mini Program runtime is required');

  state.status = 'initializing';
  const generation = ++state.generation;
  state.options = options;
  state.signature = signature;
  state.wx = wx;
  state.codec = disabledLegacyCodec(options.appKey);
  state.storage = new WxStorage(wx);
  state.metrics = freshMetrics();
  state.userId = String(state.storage.get('__synap_user_id') || '');
  state.device = new DeviceCollector({ wx, storage: state.storage, options });
  await state.device.prepare();
  if (generation !== state.generation) throw new Error('SynapAgent initialization was cancelled');
  state.sessionId = String(state.device.startId);
  state.sequence = 1;
  state.startTracking = loadOrCreateStartTracking();
  state.startAcknowledged = state.startTracking.acknowledged === true;
  state.startRetryCount = 0;
  state.foreground = true;
  state.session = {
    startedAt: Date.now(), lastWall: Date.now(),
    lastMono: typeof performance !== 'undefined' && performance.now ? performance.now() : Date.now(),
    showCount: 1, hideCount: 0, routeChanges: 0, networkChanges: 0, memoryWarnings: 0,
    clockRollback: false, route: routeTemplate()
  };
  state.listeners = new ListenerRegistry(wx);
  state.errors = new ErrorMonitor(enqueue, state.timers);
  state.behavior = new BehaviorMonitor(enqueue);
  state.integrity = new IntegrityMonitor(wx, options);
  state.network = options.capabilities.network ? new NetworkMonitor(wx, options.baseUrl) : null;
  if (state.network) {
    state.network.install();
    state.integrity.refreshBaseline();
  }
  state.transport = new WxTransport({
    wx, baseUrl: options.baseUrl, timeout: options.timeout, codec: state.codec,
    onError: (error, endpoint) => {
      state.metrics.errors[error.type] = (state.metrics.errors[error.type] || 0) + 1;
      if (state.errors && state.status === 'running') state.errors.capture('sdk_request', error, endpoint);
    }
  });
  state.queue = new MessageQueue({
    limit: options.queueLimit,
    batchLimit: options.batchLimit,
    storage: state.storage,
    storageKey: `__synap_pending_${SDK_VERSION}_${options.appKeyId}_${state.device.miniProgramInfo().appId || 'unknown'}`,
    codec: state.codec,
    onDrop: (reason) => {
      state.metrics.droppedMessages += 1;
      if (reason === 'expired') state.metrics.expiredMessages += 1;
    }
  });
  state.metrics.restoredMessages = state.queue.restore(Date.now(), DAY);
  state.sequence = state.queue.snapshot().reduce((maximum, entry) => {
    if (entry.sessionId !== state.sessionId) return maximum;
    return Math.max(maximum, Number(entry.message && entry.message.sequence) || 0);
  }, 1);

  try {
    state.policy = await requestPolicy();
  } catch (error) {
    if (generation !== state.generation) throw error;
    state.policy = readPolicyCache();
    if (!state.policy) {
      await destroy({ flush: false });
      throw error;
    }
  }
  if (generation !== state.generation) throw new Error('SynapAgent initialization was cancelled');
  state.integrity.setPolicyFingerprint(policyFingerprint());
  installLifecycle();
  state.status = 'running';
  startCollectors();
  if (!state.startAcknowledged) scheduleStartRetry();
  return api;
}

function init(options) {
  return Promise.reject(new Error(
    'Synap security protocol v1 does not support WeChat Mini Program'));
  /* Retained implementation below is migration-only and cannot be reached in v1. */
  let normalized;
  try { normalized = normalizeInitializedOptions(options); } catch (error) { return Promise.reject(error); }
  const signature = optionsSignature(normalized);
  if (state.status === 'running' && state.signature === signature) return Promise.resolve(api);
  if (state.status === 'initializing' && state.initPromise && state.signature === signature) return state.initPromise;
  const promise = state.status === 'initializing' && state.initPromise
    ? state.initPromise.catch(() => {}).then(() => initialize(options))
    : initialize(options);
  state.initPromise = promise;
  return promise.finally(() => { if (state.initPromise === promise) state.initPromise = null; });
}
/* eslint-enable no-unreachable */

async function destroy(options) {
  if (state.status === 'idle' || state.status === 'destroyed') return;
  const shouldFlush = !options || options.flush !== false;
  state.status = 'destroying';
  state.generation += 1;
  if (state.errors) state.errors.flush();
  if (state.behavior) state.behavior.flush();
  if (shouldFlush) {
    try { await flush(); } catch (_) { /* data remains persisted */ }
  }
  if (state.queue) state.queue.persist(Date.now());
  clearTimers();
  if (state.listeners) state.listeners.clear();
  if (state.network) state.network.destroy();
  if (state.transport) state.transport.destroy();
  state.uploadPromise = null;
  state.listeners = null;
  state.network = null;
  state.errors = null;
  state.behavior = null;
  state.integrity = null;
  state.transport = null;
  state.queue = null;
  state.startTracking = null;
  state.startAcknowledged = false;
  state.status = 'destroyed';
}

async function requestInfo(endpoint) {
  if (state.status !== 'running') throw new Error('SynapAgent is not initialized');
  return state.transport.post(endpoint, {
    common: state.device.common(state.userId),
    meta: createMeta(state.options)
  }, state.device.headerCode());
}

function containsSensitiveKey(value, depth) {
  if (!value || typeof value !== 'object' || depth > 6) return false;
  if (Array.isArray(value)) return value.some((item) => containsSensitiveKey(item, depth + 1));
  return Object.keys(value).some((key) => /(password|passwd|pwd|token|authorization|cookie|secret|id.?card|credit.?card|phone|email|avatar|nickname)/i.test(key)
    || containsSensitiveKey(value[key], depth + 1));
}

function userData(opaqueUserId, data) {
  if (state.status !== 'running' || typeof opaqueUserId !== 'string' || !opaqueUserId || opaqueUserId.length > 256
      || data == null || containsSensitiveKey(data, 0)) return false;
  if (/\b1[3-9]\d{9}\b/.test(opaqueUserId) || /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(opaqueUserId)) return false;
  const cloned = safeClone(sanitizeData(data, 0), 32 * 1024);
  if (cloned == null) return false;
  state.userId = sanitizeText(opaqueUserId, 256);
  state.storage.set('__synap_user_id', state.userId);
  return enqueue({ type: 'UserData', buildDate: new Date().toISOString(), userId: state.userId, data: cloned });
}

function markAction(action) {
  if (state.status !== 'running' || !action || typeof action !== 'object') return false;
  const id = sanitizeText(action.id, 128);
  const name = sanitizeText(action.name, 64);
  const phase = String(action.phase || '');
  if (!id || !name || !['start', 'success', 'failure'].includes(phase)) return false;
  const stats = state.behavior ? state.behavior.stats : {};
  return enqueue({ type: 'ActionData', buildDate: new Date().toISOString(), data: {
    id, name, phase,
    interactionCount: Number(stats.sampleCount) || 0,
    clickCount: Number(stats.clickCount) || 0,
    inputCount: Number(stats.inputCount) || 0
  } });
}

function trackInteraction(event, options) {
  if (state.status !== 'running' || !state.options.capabilities.behavior || !state.behavior) return false;
  return state.behavior.track(event, options || {});
}

function getMetrics() {
  const value = Object.assign({}, state.metrics || freshMetrics(), {
    status: state.status,
    queueSize: state.queue ? state.queue.size() : 0,
    requestTasks: state.transport ? state.transport.tasks.size : 0,
    transportTrust: 'unsupported/sc1',
    detectorVersion: DETECTOR_VERSION
  });
  return JSON.parse(JSON.stringify(value));
}

const api = Object.freeze({
  version: SDK_VERSION,
  init,
  destroy,
  flush,
  getDeviceInfo: () => requestInfo(ENDPOINTS.info),
  getRatingInfo: () => requestInfo(ENDPOINTS.rating),
  getLabelInfo: () => requestInfo(ENDPOINTS.label),
  userData,
  markAction,
  trackInteraction,
  getMetrics
});

module.exports = api;
