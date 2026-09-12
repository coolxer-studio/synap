import baseData from '../base-data';
import { sanitizeText, textHash } from '../privacy';

const rounded = (value) => Math.round((Number(value) || 0) * 100) / 100;
const monotonicNow = () => typeof performance !== 'undefined' && performance.now ? performance.now() : Date.now();
const average = (values) => values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0;
const variance = (values) => {
  if (values.length < 2) return 0;
  const mean = average(values);
  return average(values.map((value) => (value - mean) ** 2));
};
const entropy = (values) => {
  if (values.length < 2) return 0;
  const buckets = new Map();
  values.forEach((value) => { const key = Math.round(value / 25); buckets.set(key, (buckets.get(key) || 0) + 1); });
  return -Array.from(buckets.values()).reduce((sum, count) => {
    const probability = count / values.length;
    return sum + probability * Math.log2(probability);
  }, 0);
};

export default function behaviorMonitor(msgCallback, interval = 10000) {
  let state;
  let lastPoint = null;
  let lastSpeed = null;
  let lastClickAt = 0;
  let lastInputAt = 0;
  let lastPointerDownAt = 0;
  let pendingPointer = null;
  let frameId = null;
  const listeners = [];
  const actions = new Map();

  const reset = () => {
    const now = monotonicNow();
    state = {
      startedAt: now, eventCount: 0, pointerSamples: 0, clickCount: 0, inputCount: 0,
      touchCount: 0, trustedEventCount: 0, untrustedEventCount: 0, impossibleClickCount: 0,
      totalDistance: 0, speeds: [], totalAcceleration: 0, accelerationSamples: 0,
      directionChanges: 0, straightSegments: 0, segmentCount: 0, pauseMs: 0,
      clickIntervals: [], inputIntervals: [], pointerIntervals: [], forceTotal: 0, forceSamples: 0
    };
    lastPoint = null; lastSpeed = null; lastClickAt = 0; lastInputAt = 0; lastPointerDownAt = 0;
  };

  const countTrust = (event) => {
    if (event && event.isTrusted === false) state.untrustedEventCount += 1;
    else state.trustedEventCount += 1;
  };
  const add = (target, type, listener, options) => {
    target.addEventListener(type, listener, options);
    listeners.push(() => target.removeEventListener(type, listener, options));
  };
  const samplePointer = (event, at = monotonicNow()) => {
    if (!event || document.visibilityState === 'hidden') return;
    const point = { x: Number(event.clientX) || 0, y: Number(event.clientY) || 0, at };
    state.eventCount += 1; state.pointerSamples += 1; countTrust(event);
    if (event.pointerType === 'touch') state.touchCount += 1;
    if (Number(event.pressure) > 0) { state.forceTotal += Number(event.pressure); state.forceSamples += 1; }
    if (lastPoint) {
      const dx = point.x - lastPoint.x; const dy = point.y - lastPoint.y;
      const elapsed = Math.max(1, at - lastPoint.at); const distance = Math.sqrt(dx * dx + dy * dy);
      const speed = distance / elapsed;
      state.totalDistance += distance; state.speeds.push(speed); state.pointerIntervals.push(elapsed);
      if (elapsed >= 500) state.pauseMs += elapsed;
      if (lastSpeed != null) { state.totalAcceleration += Math.abs(speed - lastSpeed) / elapsed; state.accelerationSamples += 1; }
      if (lastPoint.dx != null) {
        const cross = Math.abs(lastPoint.dx * dy - lastPoint.dy * dx);
        const scale = Math.max(1, Math.sqrt(lastPoint.dx ** 2 + lastPoint.dy ** 2) * Math.max(1, distance));
        state.segmentCount += 1;
        if (cross / scale < 0.03) state.straightSegments += 1; else state.directionChanges += 1;
      }
      point.dx = dx; point.dy = dy; lastSpeed = speed;
    }
    lastPoint = point;
  };
  const schedulePointer = (event) => {
    pendingPointer = event;
    if (frameId != null) return;
    const schedule = window.requestAnimationFrame || ((callback) => setTimeout(() => callback(monotonicNow()), 16));
    frameId = schedule((timestamp) => {
      frameId = null;
      const value = pendingPointer; pendingPointer = null;
      samplePointer(value, Number(timestamp) || monotonicNow());
    });
  };
  const pointerDown = (event) => { lastPointerDownAt = monotonicNow(); samplePointer(event, lastPointerDownAt); };
  const pointerUp = (event) => samplePointer(event);
  const click = (event) => {
    const now = monotonicNow(); state.eventCount += 1; state.clickCount += 1; countTrust(event);
    const activated = !navigator.userActivation || navigator.userActivation.isActive || navigator.userActivation.hasBeenActive;
    if (now - lastPointerDownAt > 2000 && !activated) state.impossibleClickCount += 1;
    if (lastClickAt) state.clickIntervals.push(now - lastClickAt); lastClickAt = now;
  };
  const input = (event) => {
    const now = monotonicNow(); state.eventCount += 1; state.inputCount += 1; countTrust(event);
    if (lastInputAt) state.inputIntervals.push(now - lastInputAt); lastInputAt = now;
  };
  const snapshot = () => ({
    eventCount: state.eventCount, pointerSamples: state.pointerSamples, clickCount: state.clickCount,
    inputCount: state.inputCount, trustedEventCount: state.trustedEventCount, untrustedEventCount: state.untrustedEventCount
  });
  const data = () => {
    const durationMs = Math.max(1, monotonicNow() - state.startedAt);
    const intervals = [...state.pointerIntervals, ...state.clickIntervals, ...state.inputIntervals];
    return {
      durationMs: Math.round(durationMs), eventCount: state.eventCount, pointerSamples: state.pointerSamples,
      clickCount: state.clickCount, inputCount: state.inputCount, touchCount: state.touchCount,
      trustedEventCount: state.trustedEventCount, untrustedEventCount: state.untrustedEventCount,
      impossibleClickCount: state.impossibleClickCount, totalDistance: rounded(state.totalDistance),
      averageSpeed: rounded(average(state.speeds)), maxSpeed: rounded(Math.max(0, ...state.speeds)),
      speedVariance: rounded(variance(state.speeds)),
      averageAcceleration: rounded(state.accelerationSamples ? state.totalAcceleration / state.accelerationSamples : 0),
      directionChangeRatio: rounded(state.segmentCount ? state.directionChanges / state.segmentCount : 0),
      straightLineRatio: rounded(state.segmentCount ? state.straightSegments / state.segmentCount : 0),
      averageClickInterval: rounded(average(state.clickIntervals)), averageInputInterval: rounded(average(state.inputIntervals)),
      averageForce: rounded(state.forceSamples ? state.forceTotal / state.forceSamples : 0),
      pauseRatio: rounded(Math.min(1, state.pauseMs / durationMs)), timingEntropy: rounded(entropy(intervals)),
      idleRatio: rounded(Math.max(0, 1 - Math.min(1, state.eventCount / Math.max(1, durationMs / 250))))
    };
  };
  const flush = () => {
    if (document.visibilityState === 'hidden') { reset(); return; }
    if (state.eventCount > 0) msgCallback({ type: 'BehaviorData', buildDate: baseData.currentFormatDate(), data: data() });
    const now = monotonicNow();
    actions.forEach((action, id) => { if (now - action.at > 60000) actions.delete(id); });
    reset();
  };
  const markAction = ({ id, name, phase } = {}) => {
    const actionId = String(id || ''); const actionName = sanitizeText(name, 64);
    if (!actionId || actionId.length > 256 || !actionName || !['start', 'success', 'failure'].includes(phase)) return false;
    if (phase === 'start') { actions.set(actionId, { name: actionName, at: monotonicNow(), baseline: snapshot() }); return true; }
    const action = actions.get(actionId); if (!action || action.name !== actionName) return false;
    actions.delete(actionId); const current = snapshot(); const summary = {};
    Object.keys(current).forEach((key) => { summary[key] = Math.max(0, current[key] - action.baseline[key]); });
    msgCallback({ type: 'ActionData', buildDate: baseData.currentFormatDate(), data: {
      detectorVersion: 'action-1', actionIdHash: textHash(actionId), name: actionName, phase,
      durationMs: Math.round(monotonicNow() - action.at), behavior: summary
    } });
    return true;
  };

  reset();
  add(window, 'pointermove', schedulePointer, { passive: true }); add(window, 'pointerdown', pointerDown, { passive: true });
  add(window, 'pointerup', pointerUp, { passive: true }); add(window, 'click', click, { passive: true });
  add(window, 'input', input, { passive: true });
  const visibility = () => { if (document.visibilityState === 'hidden') reset(); };
  add(document, 'visibilitychange', visibility);
  const timer = setInterval(flush, interval);
  const cleanup = function () {
    clearInterval(timer); listeners.forEach((remove) => remove());
    if (frameId != null) {
      if (window.cancelAnimationFrame) window.cancelAnimationFrame(frameId); else clearTimeout(frameId);
    }
    actions.clear();
  };
  cleanup.markAction = markAction;
  cleanup.flush = flush;
  return cleanup;
}
