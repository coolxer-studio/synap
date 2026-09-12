'use strict';

const { errorTemplate, hashText, pathTemplate, routeTemplate, sanitizeText } = require('./privacy');

function safeSource(value) {
  try { return Function.prototype.toString.call(value); } catch (_) { return ''; }
}

function detectFormatting(source) {
  const value = source == null ? safeSource(formattingProbe) : String(source);
  return /[\r\n]/.test(value) ? 1 : 0;
}

function formattingProbe() { return 1; }

class ListenerRegistry {
  constructor(wxApi) {
    this.wx = wxApi;
    this.entries = [];
  }

  on(onName, offName, handler) {
    if (typeof this.wx[onName] !== 'function') return false;
    try {
      this.wx[onName](handler);
      this.entries.push({ offName, handler });
      return true;
    } catch (_) {
      return false;
    }
  }

  clear() {
    this.entries.forEach((entry) => {
      try {
        if (typeof this.wx[entry.offName] === 'function') this.wx[entry.offName](entry.handler);
      } catch (_) { /* best effort */ }
    });
    this.entries = [];
  }
}

class ErrorMonitor {
  constructor(emit, timers) {
    this.emit = emit;
    this.timers = timers;
    this.pending = new Map();
  }

  capture(category, input, source) {
    const template = errorTemplate(input && (input.message || input.reason || input.errMsg || input) || 'unknown error');
    const sourceTemplate = pathTemplate(source || input && (input.source || input.url) || '');
    const fingerprint = hashText(`${category}|${template}|${sourceTemplate}`);
    const existing = this.pending.get(fingerprint);
    if (existing) {
      existing.count += 1;
      existing.lastSeenAt = Date.now();
      return fingerprint;
    }
    const record = {
      category,
      templateHash: hashText(template),
      message: template,
      source: sourceTemplate,
      fingerprint,
      count: 1,
      firstSeenAt: Date.now(),
      lastSeenAt: Date.now()
    };
    const timer = setTimeout(() => {
      this.pending.delete(fingerprint);
      this.timers.delete(timer);
      const output = Object.assign({}, record);
      delete output.timer;
      this.emit(Object.assign({ type: 'ErrorData', buildDate: new Date().toISOString() }, output));
    }, 5000);
    this.timers.add(timer);
    record.timer = timer;
    this.pending.set(fingerprint, record);
    return fingerprint;
  }

  flush() {
    this.pending.forEach((record) => {
      if (record.timer) {
        clearTimeout(record.timer);
        this.timers.delete(record.timer);
      }
      const output = Object.assign({}, record);
      delete output.timer;
      this.emit(Object.assign({ type: 'ErrorData', buildDate: new Date().toISOString() }, output));
    });
    this.pending.clear();
  }
}

class BehaviorMonitor {
  constructor(emit) {
    this.emit = emit;
    this.reset();
  }

  reset() {
    this.startedAt = Date.now();
    this.lastAt = 0;
    this.lastPoint = null;
    this.previousVector = null;
    this.stats = {
      sampleCount: 0,
      clickCount: 0,
      touchCount: 0,
      inputCount: 0,
      trustedCount: 0,
      untrustedCount: 0,
      impossibleCount: 0,
      intervalSum: 0,
      intervalSquareSum: 0,
      speedSum: 0,
      speedSquareSum: 0,
      pressureSum: 0,
      pressureCount: 0,
      stopCount: 0,
      directionChanges: 0
    };
  }

  track(event, options) {
    if (!event || typeof event !== 'object') return false;
    const type = sanitizeText(event.type || options && options.name || 'interaction', 64).toLowerCase();
    const now = Number(event.timeStamp) || Date.now();
    const trusted = event.isTrusted !== false;
    const touches = event.touches || event.changedTouches || [];
    let first = touches[0] || null;
    if (!first && event.detail && typeof event.detail.x === 'number' && typeof event.detail.y === 'number') first = event.detail;
    const point = first ? { x: Number(first.clientX == null ? first.x : first.clientX), y: Number(first.clientY == null ? first.y : first.clientY), at: now } : null;
    this.stats.sampleCount += 1;
    if (trusted) this.stats.trustedCount += 1; else this.stats.untrustedCount += 1;
    if (/tap|click/.test(type)) this.stats.clickCount += 1;
    if (/touch|pointer/.test(type)) this.stats.touchCount += 1;
    if (/input|change|confirm/.test(type)) this.stats.inputCount += 1;
    if (/tap|click/.test(type) && !event.currentTarget && !event.target) this.stats.impossibleCount += 1;
    if (this.lastAt) {
      const interval = Math.max(0, now - this.lastAt);
      this.stats.intervalSum += interval;
      this.stats.intervalSquareSum += interval * interval;
    }
    if (point && this.lastPoint && now > this.lastPoint.at) {
      const dx = point.x - this.lastPoint.x;
      const dy = point.y - this.lastPoint.y;
      const duration = now - this.lastPoint.at;
      const distance = Math.sqrt(dx * dx + dy * dy);
      const speed = distance / duration;
      this.stats.speedSum += speed;
      this.stats.speedSquareSum += speed * speed;
      if (speed < 0.01) this.stats.stopCount += 1;
      if (this.previousVector && (dx * this.previousVector.dx + dy * this.previousVector.dy) < 0) this.stats.directionChanges += 1;
      this.previousVector = { dx, dy };
    }
    if (point && Number.isFinite(point.x) && Number.isFinite(point.y)) this.lastPoint = point;
    if (first) {
      const pressure = Number(first.force == null ? first.pressure : first.force);
      if (Number.isFinite(pressure) && pressure >= 0) {
        this.stats.pressureSum += pressure;
        this.stats.pressureCount += 1;
      }
    }
    this.lastAt = now;
    return true;
  }

  flush() {
    const s = this.stats;
    if (!s.sampleCount) {
      this.reset();
      return null;
    }
    const intervals = Math.max(1, s.sampleCount - 1);
    const meanInterval = s.intervalSum / intervals;
    const meanSpeed = s.speedSum / intervals;
    const fact = {
      type: 'BehaviorData',
      buildDate: new Date().toISOString(),
      data: {
        windowStartedAt: this.startedAt,
        windowEndedAt: Date.now(),
        sampleCount: s.sampleCount,
        clickCount: s.clickCount,
        touchCount: s.touchCount,
        inputCount: s.inputCount,
        trustedCount: s.trustedCount,
        untrustedCount: s.untrustedCount,
        impossibleCount: s.impossibleCount,
        meanInterval: Math.round(meanInterval),
        intervalVariance: Math.round(Math.max(0, s.intervalSquareSum / intervals - meanInterval * meanInterval)),
        meanSpeed: Number(meanSpeed.toFixed(4)),
        speedVariance: Number(Math.max(0, s.speedSquareSum / intervals - meanSpeed * meanSpeed).toFixed(4)),
        averagePressure: s.pressureCount ? Number((s.pressureSum / s.pressureCount).toFixed(4)) : 0,
        stopRatio: Number((s.stopCount / intervals).toFixed(4)),
        directionChanges: s.directionChanges
      }
    };
    this.reset();
    this.emit(fact);
    return fact;
  }
}

class IntegrityMonitor {
  constructor(wxApi, options) {
    this.wx = wxApi;
    this.expected = {
      appKeyId: options.appKeyId,
      baseUrl: options.baseUrl,
      profile: options.collectionProfile,
      policyFingerprint: ''
    };
    this.names = ['request', 'getDeviceInfo', 'getWindowInfo', 'getSystemSetting', 'onAppShow', 'onAppHide', 'onError'];
    this.baseline = {};
    this.refreshBaseline();
  }

  refreshBaseline() {
    this.names.forEach((name) => { this.baseline[name] = safeSource(this.wx[name]); });
  }

  setPolicyFingerprint(value) {
    this.expected.policyFingerprint = value;
  }

  collect(currentOptions, policyFingerprint) {
    const signals = [];
    const weights = {};
    this.names.forEach((name) => {
      const changed = this.baseline[name] !== safeSource(this.wx[name]);
      if (changed) {
        signals.push(`WX_API_CHANGED:${name}`);
        weights[`WX_API_CHANGED:${name}`] = name === 'request' ? 3 : 1;
      }
    });
    [['appKeyId', 3], ['baseUrl', 3], ['collectionProfile', 2]].forEach(([name, weight]) => {
      const expectedKey = name === 'collectionProfile' ? 'profile' : name;
      if (String(currentOptions[name]) !== String(this.expected[expectedKey])) {
        signals.push(`CONFIG_CHANGED:${name}`);
        weights[`CONFIG_CHANGED:${name}`] = weight;
      }
    });
    if (this.expected.policyFingerprint && policyFingerprint !== this.expected.policyFingerprint) {
      signals.push('CONFIG_CHANGED:policy');
      weights['CONFIG_CHANGED:policy'] = 3;
    }
    return {
      type: 'IntegrityData',
      buildDate: new Date().toISOString(),
      data: {
        signals,
        weights,
        evidenceCount: signals.length,
        weightedScore: signals.reduce((sum, name) => sum + weights[name], 0),
        state: 'available'
      }
    };
  }
}

class NetworkMonitor {
  constructor(wxApi, baseUrl) {
    this.wx = wxApi;
    this.baseUrl = baseUrl;
    this.originals = {};
    this.stats = new Map();
  }

  install() {
    ['request', 'uploadFile', 'downloadFile', 'connectSocket'].forEach((name) => {
      const original = this.wx[name];
      if (typeof original !== 'function') return;
      this.originals[name] = original;
      this.wx[name] = (options) => {
        const value = Object.assign({}, options || {});
        const url = String(value.url || '');
        if (!url.startsWith(this.baseUrl)) {
          const startedAt = Date.now();
          const key = `${name}:${pathTemplate(url)}`;
          const previousComplete = value.complete;
          value.complete = (response) => {
            const status = Number(response && response.statusCode) || 0;
            const item = this.stats.get(key) || { kind: name, path: pathTemplate(url), total: 0, failures: 0, duration: 0 };
            item.total += 1;
            item.duration += Date.now() - startedAt;
            if (status === 0 || status >= 400) item.failures += 1;
            this.stats.set(key, item);
            if (typeof previousComplete === 'function') previousComplete(response);
          };
        }
        return original.call(this.wx, value);
      };
    });
  }

  flush() {
    if (!this.stats.size) return null;
    const entries = Array.from(this.stats.values()).map((item) => Object.assign({}, item, {
      averageDuration: item.total ? Math.round(item.duration / item.total) : 0,
      status: 'available'
    }));
    this.stats.clear();
    return { type: 'NetworkData', buildDate: new Date().toISOString(), data: { entries } };
  }

  destroy() {
    Object.keys(this.originals).forEach((name) => { this.wx[name] = this.originals[name]; });
    this.originals = {};
    this.stats.clear();
  }
}

function runtimeFact(deviceCollector) {
  const device = deviceCollector.device;
  const mini = deviceCollector.miniProgramInfo();
  const scene = Number(deviceCollector.launch.scene) || 0;
  const devtools = String(device.brand || '').toLowerCase() === 'devtools' || String(device.platform || '').toLowerCase() === 'devtools';
  return {
    type: 'RuntimeData',
    buildDate: new Date().toISOString(),
    data: {
      devtools,
      enableDebug: Boolean(deviceCollector.appBaseInfo.enableDebug),
      nonReleaseEnv: Boolean(mini.envVersion && mini.envVersion !== 'release'),
      automationScene: scene === 1030,
      scene,
      codeFormatting: detectFormatting(),
      route: routeTemplate(),
      state: 'available'
    }
  };
}

module.exports = {
  detectFormatting,
  ListenerRegistry,
  ErrorMonitor,
  BehaviorMonitor,
  IntegrityMonitor,
  NetworkMonitor,
  runtimeFact
};
