import baseData from './base-data';
import { sanitizeText, textHash, urlTemplate } from './privacy';
import { electronIntegrityEvidence } from './host-context';

const loadedScript = typeof document !== 'undefined' ? document.currentScript : null;
const scriptContext = {
  src: loadedScript && loadedScript.src ? urlTemplate(loadedScript.src) : '',
  origin: (() => { try { return loadedScript && loadedScript.src ? new URL(loadedScript.src).origin : ''; } catch (_) { return ''; } })(),
  integrity: loadedScript && loadedScript.integrity ? String(loadedScript.integrity) : '',
  crossOrigin: loadedScript && loadedScript.crossOrigin ? String(loadedScript.crossOrigin) : ''
};
const prepared = { uaHighEntropy: null, uaHighEntropyStatus: 'disabled' };

function functionChanged(fn) {
  if (typeof fn !== 'function') return true;
  if (fn.__synapWrapped === true) return false;
  try { return !/\{\s*\[native code\]\s*\}/.test(Function.prototype.toString.call(fn)); }
  catch (_) { return true; }
}

export async function prepareIntegrity(profile = {}, timeout = 1000) {
  const userAgentData = navigator.userAgentData;
  if (!profile.uaHighEntropy) {
    prepared.uaHighEntropyStatus = 'disabled';
    return;
  }
  if (!window.isSecureContext || !userAgentData || typeof userAgentData.getHighEntropyValues !== 'function') {
    prepared.uaHighEntropyStatus = 'unavailable';
    return;
  }
  try {
    prepared.uaHighEntropy = await Promise.race([
      userAgentData.getHighEntropyValues(['architecture', 'bitness', 'model', 'platformVersion', 'uaFullVersion']),
      new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), timeout))
    ]);
    prepared.uaHighEntropyStatus = 'available';
  } catch (error) {
    prepared.uaHighEntropyStatus = error && error.message === 'timeout' ? 'blocked' : 'error';
  }
}

function apiSignal(target, key) {
  if (!target) return { changed: false, status: 'unavailable' };
  let descriptor;
  try { descriptor = Object.getOwnPropertyDescriptor(target, key); } catch (_) { return { changed: false, status: 'error' }; }
  const value = descriptor && (descriptor.value || descriptor.get);
  return { changed: typeof value === 'function' ? functionChanged(value) : false, status: descriptor ? 'available' : 'unavailable' };
}

export function collectIntegrity(appId, expectedPolicyFingerprint, hostContext = null) {
  const userAgent = String(navigator.userAgent || '');
  const platform = String(navigator.platform || '');
  const userAgentData = navigator.userAgentData || null;
  const fetchApi = apiSignal(window, 'fetch');
  const xhrApi = apiSignal(window.XMLHttpRequest && window.XMLHttpRequest.prototype, 'open');
  const eventApi = apiSignal(window.EventTarget && window.EventTarget.prototype, 'addEventListener');
  const configuredAppId = window.SynapAgentConfig && window.SynapAgentConfig.appId;
  const languageMismatch = Array.isArray(navigator.languages) && navigator.languages.length > 0
    && navigator.language && navigator.languages[0] !== navigator.language;
  const uaChPlatform = userAgentData && userAgentData.platform ? String(userAgentData.platform) : '';
  const signals = {
    webdriver: navigator.webdriver === true,
    headlessUserAgent: /headless|phantomjs/i.test(userAgent),
    platformMismatch: /Android/i.test(userAgent) && /Mac|Win/i.test(platform)
      || /Windows/i.test(userAgent) && /Mac|Linux/i.test(platform),
    uaChPlatformMismatch: !!uaChPlatform && /Android/i.test(userAgent) !== /Android/i.test(uaChPlatform),
    uaChMobileMismatch: !!userAgentData && Boolean(userAgentData.mobile) !== /Mobile|Android|iPhone|iPad/i.test(userAgent),
    languageMismatch,
    touchMismatch: /Mobile|Android|iPhone|iPad/i.test(userAgent) && Number(navigator.maxTouchPoints || 0) === 0,
    screenInvalid: !window.screen || Number(window.screen.width) <= 0 || Number(window.screen.height) <= 0,
    fetchModified: fetchApi.changed,
    xhrModified: xhrApi.changed,
    eventTargetModified: eventApi.changed,
    appIdModified: configuredAppId != null
      && String(configuredAppId) !== String(appId || ''),
    policyUnavailable: !expectedPolicyFingerprint
  };
  const weights = {
    webdriver: 5, headlessUserAgent: 4, platformMismatch: 2, uaChPlatformMismatch: 3,
    uaChMobileMismatch: 2, languageMismatch: 1, touchMismatch: 1, screenInvalid: 1,
    fetchModified: 1, xhrModified: 2, eventTargetModified: 3,
    appIdModified: 4, policyUnavailable: 1
  };
  const electronEvidence = electronIntegrityEvidence(hostContext);
  Object.assign(signals, electronEvidence.signals);
  Object.assign(weights, electronEvidence.weights);
  const score = Object.keys(signals).reduce((total, key) => total + (signals[key] ? weights[key] || 1 : 0), 0);
  const evidenceCount = Object.values(signals).filter(Boolean).length;
  return {
    type: 'IntegrityData',
    buildDate: baseData.currentFormatDate(),
    data: {
      detectorVersion: 'integrity-2', score, evidenceCount, signals, weights,
      confidence: Math.round(Math.min(1, score / 10) * 100) / 100,
      platform, language: navigator.language || '',
      userAgentData: userAgentData ? { brands: userAgentData.brands || [], mobile: !!userAgentData.mobile, platform: uaChPlatform } : null,
      highEntropy: prepared.uaHighEntropy,
      capabilities: {
        uaClientHints: { status: userAgentData ? 'available' : 'unavailable' },
        uaHighEntropy: { status: prepared.uaHighEntropyStatus },
        fetchDescriptor: { status: fetchApi.status }, xhrDescriptor: { status: xhrApi.status },
        eventTargetDescriptor: { status: eventApi.status }
      }
    }
  };
}

function originOf(value) { try { return new URL(String(value || ''), window.location.href).origin; } catch (_) { return ''; } }

export function collectResourceIntegrity(options = {}) {
  const allowedScripts = options.allowedScriptOrigins || [];
  const allowedParents = options.allowedParentOrigins || [];
  const allowedServiceWorkers = options.allowedServiceWorkerOrigins || [];
  const expectedIntegrity = String(options.expectedScriptIntegrity || '');
  const ancestors = [];
  try {
    const values = window.location.ancestorOrigins;
    for (let index = 0; values && index < values.length && index < 8; index += 1) ancestors.push(originOf(values[index]));
  } catch (_) { }
  const parentOrigin = ancestors[0] || originOf(document.referrer);
  const navigation = (() => {
    try {
      const entry = performance.getEntriesByType('navigation')[0];
      return entry ? { type: entry.type || '', redirectCount: Number(entry.redirectCount) || 0 } : { type: '', redirectCount: 0 };
    } catch (_) { return { type: '', redirectCount: 0 }; }
  })();
  const scriptOriginAllowed = !allowedScripts.length || allowedScripts.includes(scriptContext.origin);
  const parentOriginAllowed = window.top === window || !allowedParents.length || allowedParents.includes(parentOrigin);
  const integrityMatches = !expectedIntegrity || scriptContext.integrity === expectedIntegrity;
  const serviceWorker = (() => {
    try {
      const controller = navigator.serviceWorker && navigator.serviceWorker.controller;
      return controller ? { status: 'controlled', script: urlTemplate(controller.scriptURL) } : { status: 'none', script: '' };
    } catch (_) { return { status: 'blocked', script: '' }; }
  })();
  const signals = {
    scriptOriginUnexpected: !scriptOriginAllowed,
    scriptIntegrityMismatch: !integrityMatches,
    parentOriginUnexpected: !parentOriginAllowed,
    serviceWorkerUnexpected: serviceWorker.status === 'controlled' && allowedServiceWorkers.length > 0
      && !allowedServiceWorkers.includes(originOf(serviceWorker.script))
  };
  return {
    type: 'ResourceIntegrityData', buildDate: baseData.currentFormatDate(),
    data: {
      detectorVersion: 'resource-1', script: scriptContext, serviceWorker,
      embed: {
        framed: window.top !== window, ancestors, parentOrigin, parentOriginAllowed,
        referrerOrigin: originOf(document.referrer), sandboxed: !!(window.frameElement && window.frameElement.hasAttribute('sandbox')),
        secureContext: !!window.isSecureContext, crossOriginIsolated: !!window.crossOriginIsolated
      },
      policy: {
        cspMetaPresent: !!document.querySelector('meta[http-equiv="Content-Security-Policy" i]'),
        permissionsPolicyStatus: document.permissionsPolicy || document.featurePolicy ? 'available' : 'unavailable'
      },
      navigation,
      userActivation: navigator.userActivation ? { active: !!navigator.userActivation.isActive, hasBeenActive: !!navigator.userActivation.hasBeenActive } : null,
      signals,
      capabilities: {
        scriptAllowlist: { status: allowedScripts.length ? 'available' : 'disabled' },
        expectedIntegrity: { status: expectedIntegrity ? 'available' : 'disabled' },
        parentAllowlist: { status: allowedParents.length ? 'available' : 'disabled' },
        serviceWorkerAllowlist: { status: allowedServiceWorkers.length ? 'available' : 'disabled' },
        reportingObserver: { status: typeof ReportingObserver === 'function' ? 'available' : 'unavailable' }
      }
    }
  };
}

export function resourceReportMonitor(msgCallback, interval = 10000) {
  let reports = new Map();
  let observer = null;
  const record = (category, source, detail) => {
    const key = `${category}|${urlTemplate(source || '')}|${sanitizeText(detail || '', 128)}`;
    const current = reports.get(key) || { category, source: urlTemplate(source || ''), detailHash: textHash(detail || ''), count: 0 };
    current.count += 1;
    reports.set(key, current);
  };
  const violation = (event) => record('csp', event.blockedURI || event.sourceFile, event.effectiveDirective || event.violatedDirective);
  const resource = (event) => {
    if (event.target && event.target !== window) record('resource-error', event.target.currentSrc || event.target.src || event.target.href, event.target.tagName || 'resource');
  };
  document.addEventListener('securitypolicyviolation', violation);
  window.addEventListener('error', resource, true);
  if (typeof ReportingObserver === 'function') {
    try {
      observer = new ReportingObserver((values) => values.forEach((report) => {
        const body = report.body || {};
        record(`reporting-${report.type || 'unknown'}`, report.url || body.sourceFile || body.blockedURL, body.id || body.effectiveDirective || report.type);
      }), { buffered: true });
      observer.observe();
    } catch (_) { observer = null; }
  }
  const flush = () => {
    if (document.visibilityState === 'hidden' || !reports.size) return;
    const values = Array.from(reports.values()).slice(0, 30);
    reports = new Map();
    msgCallback({ type: 'ResourceIntegrityData', buildDate: baseData.currentFormatDate(), data: { detectorVersion: 'resource-report-1', reports: values } });
  };
  const timer = setInterval(flush, interval);
  return () => {
    clearInterval(timer); document.removeEventListener('securitypolicyviolation', violation);
    window.removeEventListener('error', resource, true); if (observer) observer.disconnect();
  };
}

export function sessionMonitor(msgCallback, appKey, interval = 10000, serverTime = 0) {
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
      serverTimeOffsetMs: serverTime ? startWall - Number(serverTime) : null,
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
  const originalBeacon = navigator.sendBeacon;
  const OriginalWebSocket = window.WebSocket;
  const OriginalEventSource = window.EventSource;
  let stats = new Map();
  const excluded = (url) => excludedUrls.some((value) => String(url).startsWith(value));
  const record = (url, status, duration, failed, transport = 'xhr') => {
    if (excluded(url)) return;
    const key = `${endpointTemplate(url)}|${Math.floor((Number(status) || 0) / 100)}xx|${transport}`;
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
        record(url, response.status, Date.now() - startedAt, !response.ok, 'fetch');
        return response;
      }, (error) => {
        record(url, 0, Date.now() - startedAt, true, 'fetch');
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
        this.addEventListener('loadend', () => record(meta.url, this.status, Date.now() - meta.startedAt, this.status < 200 || this.status >= 400, 'xhr'), { once: true });
      }
      return originalSend.apply(this, args);
    };
    wrappedSend.__synapWrapped = true;
    try {
      window.XMLHttpRequest.prototype.open = wrappedOpen;
      window.XMLHttpRequest.prototype.send = wrappedSend;
    } catch (_) { }
  }

  let wrappedBeacon = null;
  if (typeof originalBeacon === 'function') {
    wrappedBeacon = function (url, data) {
      const startedAt = Date.now();
      try {
        const accepted = originalBeacon.call(navigator, url, data);
        record(url, accepted ? 200 : 0, Date.now() - startedAt, !accepted, 'beacon');
        return accepted;
      } catch (error) {
        record(url, 0, Date.now() - startedAt, true, 'beacon');
        throw error;
      }
    };
    wrappedBeacon.__synapWrapped = true;
    try { navigator.sendBeacon = wrappedBeacon; } catch (_) { wrappedBeacon = null; }
  }

  let WrappedWebSocket = null;
  if (typeof OriginalWebSocket === 'function') {
    WrappedWebSocket = function (url, protocols) {
      const startedAt = Date.now();
      const socket = protocols === undefined ? new OriginalWebSocket(url) : new OriginalWebSocket(url, protocols);
      let recorded = false;
      const done = (status, failed) => {
        if (recorded) return; recorded = true; record(url, status, Date.now() - startedAt, failed, 'websocket');
      };
      socket.addEventListener('error', () => done(0, true), { once: true });
      socket.addEventListener('close', (event) => done(event.code === 1000 ? 200 : 0, event.code !== 1000), { once: true });
      return socket;
    };
    WrappedWebSocket.__synapWrapped = true;
    WrappedWebSocket.prototype = OriginalWebSocket.prototype;
    try { Object.setPrototypeOf(WrappedWebSocket, OriginalWebSocket); window.WebSocket = WrappedWebSocket; } catch (_) { WrappedWebSocket = null; }
  }

  let WrappedEventSource = null;
  if (typeof OriginalEventSource === 'function') {
    WrappedEventSource = function (url, options) {
      const startedAt = Date.now(); const source = new OriginalEventSource(url, options); let recorded = false;
      source.addEventListener('open', () => { if (!recorded) { recorded = true; record(url, 200, Date.now() - startedAt, false, 'eventsource'); } }, { once: true });
      source.addEventListener('error', () => { if (!recorded) { recorded = true; record(url, 0, Date.now() - startedAt, true, 'eventsource'); } }, { once: true });
      return source;
    };
    WrappedEventSource.__synapWrapped = true;
    WrappedEventSource.prototype = OriginalEventSource.prototype;
    try { Object.setPrototypeOf(WrappedEventSource, OriginalEventSource); window.EventSource = WrappedEventSource; } catch (_) { WrappedEventSource = null; }
  }

  const resourceFailure = (event) => {
    if (event.target && event.target !== window) {
      const source = event.target.currentSrc || event.target.src || event.target.href;
      if (source) record(source, 0, 0, true, 'resource');
    }
  };
  window.addEventListener('error', resourceFailure, true);

  const flush = () => {
    if (document.visibilityState === 'hidden' || stats.size === 0) return;
    const endpoints = Array.from(stats.entries()).slice(0, 20).map(([key, value]) => ({
      endpoint: key.split('|')[0],
      statusGroup: key.split('|')[1],
      transport: key.split('|')[2],
      count: value.count,
      failures: value.failures,
      averageDurationMs: Math.round(value.totalDurationMs / value.count),
      maxDurationMs: value.maxDurationMs
    }));
    stats = new Map();
    const serviceWorkerControlled = !!(navigator.serviceWorker && navigator.serviceWorker.controller);
    msgCallback({ type: 'NetworkData', buildDate: baseData.currentFormatDate(), data: { endpoints, serviceWorkerControlled } });
  };
  const timer = setInterval(flush, interval);

  return function cleanup() {
    clearInterval(timer);
    if (wrappedFetch && window.fetch === wrappedFetch) window.fetch = originalFetch;
    if (wrappedOpen && window.XMLHttpRequest.prototype.open === wrappedOpen) window.XMLHttpRequest.prototype.open = originalOpen;
    if (wrappedSend && window.XMLHttpRequest.prototype.send === wrappedSend) window.XMLHttpRequest.prototype.send = originalSend;
    if (wrappedBeacon && navigator.sendBeacon === wrappedBeacon) navigator.sendBeacon = originalBeacon;
    if (WrappedWebSocket && window.WebSocket === WrappedWebSocket) window.WebSocket = OriginalWebSocket;
    if (WrappedEventSource && window.EventSource === WrappedEventSource) window.EventSource = OriginalEventSource;
    window.removeEventListener('error', resourceFailure, true);
  };
}
