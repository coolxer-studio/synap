import baseData from '../base-data';
import { sanitizeText, textHash, urlTemplate } from '../privacy';

window.ct_error_config = { message: ['.*'] };

function matchesConfig(message) {
  const patterns = window.ct_error_config && Array.isArray(window.ct_error_config.message)
    ? window.ct_error_config.message : [];
  return patterns.some((expression) => {
    try { return new RegExp(expression).test(String(message)); } catch (_) { return false; }
  });
}

export default {
  monitor(msgCallback) {
    const pending = new Map();
    const emit = (payload) => {
      if (!matchesConfig(payload.message)) return;
      const template = sanitizeText(payload.message, 256);
      const source = payload.source ? urlTemplate(payload.source) : '';
      const fingerprint = [payload.category, template, source, payload.lineno, payload.colno].join('|');
      const current = pending.get(fingerprint);
      if (current) {
        current.message.count += 1;
        return;
      }
      const message = {
        type: 'ErrorData',
        buildDate: baseData.currentFormatDate(),
        from: payload.from || '',
        category: payload.category || 'script',
        message: template,
        templateHash: textHash(fingerprint),
        source,
        lineno: Number(payload.lineno) || 0,
        colno: Number(payload.colno) || 0,
        count: 1
      };
      const entry = { message, timer: null };
      entry.timer = setTimeout(() => { pending.delete(fingerprint); msgCallback(message); }, 5000);
      pending.set(fingerprint, entry);
    };

    const errorListener = (event) => {
      if (event && event.target && event.target !== window) {
        const target = event.target;
        emit({ from: 'resourceError', category: 'resource', message: 'resource-load-failed', source: target.currentSrc || target.src || target.href });
        return;
      }
      emit({ from: 'windowError', category: 'script', message: event && event.message, source: event && event.filename, lineno: event && event.lineno, colno: event && event.colno });
    };
    const rejectionListener = (event) => {
      const reason = event && event.reason;
      emit({ from: 'unhandledRejection', category: 'promise', message: reason && reason.message ? reason.message : String(reason || 'unhandled-rejection') });
    };
    const cspListener = (event) => emit({
      from: 'securityPolicyViolation', category: 'csp',
      message: `${event.effectiveDirective || event.violatedDirective || 'unknown'}:${event.disposition || 'enforce'}`,
      source: event.blockedURI || event.sourceFile
    });
    window.addEventListener('error', errorListener, true);
    window.addEventListener('unhandledrejection', rejectionListener);
    document.addEventListener('securitypolicyviolation', cspListener);

    return function cleanup() {
      pending.forEach((entry) => { clearTimeout(entry.timer); msgCallback(entry.message); });
      pending.clear();
      window.removeEventListener('error', errorListener, true);
      window.removeEventListener('unhandledrejection', rejectionListener);
      document.removeEventListener('securitypolicyviolation', cspListener);
    };
  },
  loadConfig(config) { window.ct_error_config = config.errorConfig; }
};
