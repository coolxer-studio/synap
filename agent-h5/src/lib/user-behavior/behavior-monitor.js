import baseData from '../base-data';

function rounded(value) {
  return Math.round((Number(value) || 0) * 100) / 100;
}

export default function behaviorMonitor(msgCallback, interval = 10000) {
  let state;
  let lastPoint = null;
  let lastSpeed = null;
  let lastClickAt = 0;
  let lastInputAt = 0;
  const listeners = [];

  const reset = () => {
    state = {
      startedAt: Date.now(),
      eventCount: 0,
      pointerSamples: 0,
      clickCount: 0,
      inputCount: 0,
      touchCount: 0,
      totalDistance: 0,
      totalSpeed: 0,
      maxSpeed: 0,
      totalAcceleration: 0,
      accelerationSamples: 0,
      directionChanges: 0,
      clickIntervals: [],
      inputIntervals: [],
      forceTotal: 0,
      forceSamples: 0
    };
    lastPoint = null;
    lastSpeed = null;
  };

  const add = (target, type, listener, options) => {
    target.addEventListener(type, listener, options);
    listeners.push(() => target.removeEventListener(type, listener, options));
  };

  const pointer = (event) => {
    if (document.visibilityState === 'hidden') return;
    const now = Date.now();
    const point = { x: Number(event.clientX) || 0, y: Number(event.clientY) || 0, at: now };
    state.eventCount += 1;
    state.pointerSamples += 1;
    if (event.pointerType === 'touch') state.touchCount += 1;
    if (Number(event.pressure) > 0) {
      state.forceTotal += Number(event.pressure);
      state.forceSamples += 1;
    }
    if (lastPoint) {
      const dx = point.x - lastPoint.x;
      const dy = point.y - lastPoint.y;
      const elapsed = Math.max(1, now - lastPoint.at);
      const distance = Math.sqrt(dx * dx + dy * dy);
      const speed = distance / elapsed;
      state.totalDistance += distance;
      state.totalSpeed += speed;
      state.maxSpeed = Math.max(state.maxSpeed, speed);
      if (lastSpeed != null) {
        state.totalAcceleration += Math.abs(speed - lastSpeed) / elapsed;
        state.accelerationSamples += 1;
      }
      if (lastPoint.dx != null && (lastPoint.dx * dy - lastPoint.dy * dx) !== 0) state.directionChanges += 1;
      point.dx = dx;
      point.dy = dy;
      lastSpeed = speed;
    }
    lastPoint = point;
  };

  const click = () => {
    const now = Date.now();
    state.eventCount += 1;
    state.clickCount += 1;
    if (lastClickAt) state.clickIntervals.push(now - lastClickAt);
    lastClickAt = now;
  };

  const input = (event) => {
    if (!event.isTrusted) return;
    const now = Date.now();
    state.eventCount += 1;
    state.inputCount += 1;
    if (lastInputAt) state.inputIntervals.push(now - lastInputAt);
    lastInputAt = now;
  };

  const average = (values) => values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : 0;
  const flush = () => {
    const durationMs = Date.now() - state.startedAt;
    if (document.visibilityState === 'hidden') {
      reset();
      return;
    }
    if (state.eventCount > 0) {
      msgCallback({
        type: 'BehaviorData',
        buildDate: baseData.currentFormatDate(),
        data: {
          durationMs,
          eventCount: state.eventCount,
          pointerSamples: state.pointerSamples,
          clickCount: state.clickCount,
          inputCount: state.inputCount,
          touchCount: state.touchCount,
          totalDistance: rounded(state.totalDistance),
          averageSpeed: rounded(state.pointerSamples > 1 ? state.totalSpeed / (state.pointerSamples - 1) : 0),
          maxSpeed: rounded(state.maxSpeed),
          averageAcceleration: rounded(state.accelerationSamples ? state.totalAcceleration / state.accelerationSamples : 0),
          directionChangeRatio: rounded(state.pointerSamples > 1 ? state.directionChanges / (state.pointerSamples - 1) : 0),
          averageClickInterval: rounded(average(state.clickIntervals)),
          averageInputInterval: rounded(average(state.inputIntervals)),
          averageForce: rounded(state.forceSamples ? state.forceTotal / state.forceSamples : 0),
          idleRatio: rounded(Math.max(0, 1 - Math.min(1, state.eventCount / Math.max(1, durationMs / 250))))
        }
      });
    }
    reset();
  };

  reset();
  add(window, 'pointermove', pointer, { passive: true });
  add(window, 'pointerdown', pointer, { passive: true });
  add(window, 'pointerup', pointer, { passive: true });
  add(window, 'click', click, { passive: true });
  add(window, 'input', input, { passive: true });
  const timer = setInterval(flush, interval);

  return function cleanup() {
    clearInterval(timer);
    listeners.forEach((remove) => remove());
  };
}
