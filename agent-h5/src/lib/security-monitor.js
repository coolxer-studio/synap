import baseData from './base-data';

function functionChanged(fn) {
  if (typeof fn !== 'function') return true;
  if (fn.__synapWrapped === true) return false;
  try { return !/\{\s*\[native code\]\s*\}/.test(Function.prototype.toString.call(fn)); }
  catch (_) { return true; }
}

export function collectIntegrity(appKey, expectedPolicyFingerprint) {
  const userAgent = String(navigator.userAgent || '');
  const platform = String(navigator.platform || '');
  const signals = {
    webdriver: navigator.webdriver === true,
    headlessUserAgent: /headless|phantomjs/i.test(userAgent),
    platformMismatch: /Android/i.test(userAgent) && /Mac|Win/i.test(platform),
    fetchModified: typeof window.fetch === 'function' && functionChanged(window.fetch),
    xhrModified: typeof window.XMLHttpRequest === 'function' && functionChanged(window.XMLHttpRequest.prototype.open),
    eventTargetModified: typeof window.EventTarget === 'function' && functionChanged(window.EventTarget.prototype.addEventListener),
    appKeyModified: String(window.kk || '') !== String(appKey || ''),
    policyModified: (() => {
      try { return JSON.stringify(window._polic) !== expectedPolicyFingerprint; }
      catch (_) { return true; }
    })()
  };
  const score = Object.keys(signals).reduce((total, key) => total + (signals[key] ? 1 : 0), 0);
  return {
    type: 'IntegrityData',
    buildDate: baseData.currentFormatDate(),
    data: { score, signals, platform, language: navigator.language || '' }
  };
}

export function sessionMonitor(msgCallback, appKey, interval = 10000) {
  const tabId = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  const peers = new Map();
  const startWall = Date.now();
  const startMono = typeof performance !== 'undefined' ? performance.now() : 0;
  let lastWall = startWall;
  let clockRollback = false;
  let bfcacheRestores = 0;
  let focusChanges = 0;
  let visibilityChanges = 0;
  let previousFingerprint = '';
  let channel = null;

  try {
    if (typeof BroadcastChannel === 'function') {
      channel = new BroadcastChannel(`__synap_session_${appKey}`);
      channel.onmessage = (event) => {
        if (event.data && event.data.tabId !== tabId) peers.set(event.data.tabId, Date.now());
      };
    }
  } catch (_) { channel = null; }

  const announce = () => {
    const now = Date.now();
    peers.forEach((seenAt, peer) => { if (now - seenAt > 15000) peers.delete(peer); });
    try { if (channel) channel.postMessage({ tabId, at: now }); } catch (_) { }
  };
  const focus = () => { focusChanges += 1; };
  const visibility = () => { visibilityChanges += 1; };
  const pageshow = (event) => { if (event.persisted) bfcacheRestores += 1; };
  window.addEventListener('focus', focus);
  window.addEventListener('blur', focus);
  document.addEventListener('visibilitychange', visibility);
  window.addEventListener('pageshow', pageshow);

  const collect = () => {
    const now = Date.now();
    if (now < lastWall - 1000) clockRollback = true;
    lastWall = now;
    const monoElapsed = typeof performance !== 'undefined' ? performance.now() - startMono : now - startWall;
    const data = {
      visibility: document.visibilityState,
      focused: typeof document.hasFocus === 'function' ? document.hasFocus() : true,
      online: navigator.onLine !== false,
      bfcacheRestores,
      focusChanges,
      visibilityChanges,
      clockRollback,
      clockDriftMs: Math.round((now - startWall) - monoElapsed),
      concurrentTabs: peers.size + 1,
      timeOrigin: typeof performance !== 'undefined' ? Math.round(performance.timeOrigin || startWall) : startWall
    };
    const fingerprint = JSON.stringify(data);
    if (fingerprint !== previousFingerprint) {
      previousFingerprint = fingerprint;
      msgCallback({ type: 'SessionData', buildDate: baseData.currentFormatDate(), data });
    }
  };

  announce();
  collect();
  const announceTimer = setInterval(announce, 5000);
  const collectTimer = setInterval(collect, interval);
  return function cleanup() {
    clearInterval(announceTimer);
    clearInterval(collectTimer);
    window.removeEventListener('focus', focus);
    window.removeEventListener('blur', focus);
    document.removeEventListener('visibilitychange', visibility);
    window.removeEventListener('pageshow', pageshow);
    try { if (channel) channel.close(); } catch (_) { }
  };
}

function endpointTemplate(input) {
  try {
    const url = new URL(String(input), window.location.href);
    const path = url.pathname.split('/').map((segment) => {
      if (/^\d+$/.test(segment) || /^[0-9a-f]{8}-[0-9a-f-]{27,}$/i.test(segment)) return ':id';
      return segment;
    }).join('/');
    return `${url.origin}${path}`;
  } catch (_) { return 'invalid-url'; }
}

export function networkMonitor(msgCallback, excludedUrls = [], interval = 10000) {
  const originalFetch = window.fetch;
  const originalOpen = window.XMLHttpRequest && window.XMLHttpRequest.prototype.open;
  const originalSend = window.XMLHttpRequest && window.XMLHttpRequest.prototype.send;
  let stats = new Map();
  const excluded = (url) => excludedUrls.some((value) => String(url).startsWith(value));
  const record = (url, status, duration, failed) => {
    if (excluded(url)) return;
    const key = `${endpointTemplate(url)}|${Math.floor((Number(status) || 0) / 100)}xx`;
    const current = stats.get(key) || { count: 0, failures: 0, totalDurationMs: 0, maxDurationMs: 0 };
    current.count += 1;
    current.failures += failed ? 1 : 0;
    current.totalDurationMs += duration;
    current.maxDurationMs = Math.max(current.maxDurationMs, duration);
    stats.set(key, current);
  };

  let wrappedFetch = null;
  if (typeof originalFetch === 'function') {
    wrappedFetch = function (...args) {
      const startedAt = Date.now();
      const url = args[0] && args[0].url ? args[0].url : args[0];
      return originalFetch.apply(this, args).then((response) => {
        record(url, response.status, Date.now() - startedAt, !response.ok);
        return response;
      }, (error) => {
        record(url, 0, Date.now() - startedAt, true);
        throw error;
      });
    };
    wrappedFetch.__synapWrapped = true;
    try { window.fetch = wrappedFetch; } catch (_) { }
  }

  let wrappedOpen = null;
  let wrappedSend = null;
  if (originalOpen && originalSend) {
    wrappedOpen = function (method, url, ...rest) {
      this.__synapNetwork = { url, startedAt: 0 };
      return originalOpen.call(this, method, url, ...rest);
    };
    wrappedOpen.__synapWrapped = true;
    wrappedSend = function (...args) {
      const meta = this.__synapNetwork;
      if (meta) {
        meta.startedAt = Date.now();
        this.addEventListener('loadend', () => record(meta.url, this.status, Date.now() - meta.startedAt, this.status < 200 || this.status >= 400), { once: true });
      }
      return originalSend.apply(this, args);
    };
    wrappedSend.__synapWrapped = true;
    try {
      window.XMLHttpRequest.prototype.open = wrappedOpen;
      window.XMLHttpRequest.prototype.send = wrappedSend;
    } catch (_) { }
  }

  const flush = () => {
    if (document.visibilityState === 'hidden' || stats.size === 0) return;
    const endpoints = Array.from(stats.entries()).slice(0, 20).map(([key, value]) => ({
      endpoint: key.split('|')[0],
      statusGroup: key.split('|')[1],
      count: value.count,
      failures: value.failures,
      averageDurationMs: Math.round(value.totalDurationMs / value.count),
      maxDurationMs: value.maxDurationMs
    }));
    stats = new Map();
    msgCallback({ type: 'NetworkData', buildDate: baseData.currentFormatDate(), data: { endpoints } });
  };
  const timer = setInterval(flush, interval);

  return function cleanup() {
    clearInterval(timer);
    if (wrappedFetch && window.fetch === wrappedFetch) window.fetch = originalFetch;
    if (wrappedOpen && window.XMLHttpRequest.prototype.open === wrappedOpen) window.XMLHttpRequest.prototype.open = originalOpen;
    if (wrappedSend && window.XMLHttpRequest.prototype.send === wrappedSend) window.XMLHttpRequest.prototype.send = originalSend;
  };
}
