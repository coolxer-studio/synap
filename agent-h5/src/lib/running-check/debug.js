import baseData from '../base-data';

/**
 * Detect long main-thread pauses without eval, Function constructors, Blob URLs,
 * or long-lived workers. This is less invasive and remains compatible with a
 * strict Content-Security-Policy.
 */
export default function debugMonitor(msgCallback, interval = 10000, threshold = 5000) {
  let expectedAt = Date.now() + interval;
  let detected = false;
  const timer = setInterval(() => {
    const now = Date.now();
    const drift = now - expectedAt;
    expectedAt = now + interval;
    if (document.visibilityState !== 'visible') return;
    if (!detected && drift > threshold) {
      msgCallback({
        type: 'DebugData',
        buildDate: baseData.currentFormatDate(),
        debugType: 1,
        diff: drift
      });
      detected = true;
    } else if (drift <= threshold) {
      detected = false;
    }
  }, interval);

  return function cleanup() {
    clearInterval(timer);
  };
}
