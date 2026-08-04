import baseData from '../base-data';

window.ct_error_config = { message: ['.*'] };

function matchesConfig(message) {
  const patterns = window.ct_error_config && Array.isArray(window.ct_error_config.message)
    ? window.ct_error_config.message
    : [];
  return patterns.some((expression) => {
    try { return new RegExp(expression).test(String(message)); } catch (_) { return false; }
  });
}

export default {
  monitor(msgCallback) {
    const originalOnerror = window.onerror;
    const originalConsoleError = window.console && window.console.error;

    const emit = (payload) => {
      if (!matchesConfig(payload.message)) return;
      msgCallback({
        type: 'ErrorData',
        buildDate: baseData.currentFormatDate(),
        from: payload.from || '',
        message: String(payload.message || ''),
        source: payload.source || '',
        lineno: Number(payload.lineno) || 0,
        colno: Number(payload.colno) || 0
      });
    };

    const onerror = function (message, source, lineno, colno, error) {
      emit({ from: 'windowOnerror', message, source, lineno, colno });
      if (typeof originalOnerror === 'function') {
        return originalOnerror.call(window, message, source, lineno, colno, error);
      }
      return false;
    };

    const consoleError = function (...args) {
      emit({ from: 'consoleError', message: args.map(String).join(' ') });
      if (typeof originalConsoleError === 'function') {
        return originalConsoleError.apply(window.console, args);
      }
    };

    let onerrorInstalled = false;
    let consoleInstalled = false;
    try {
      window.onerror = onerror;
      onerrorInstalled = window.onerror === onerror;
    } catch (_) { }
    try {
      if (window.console) {
        window.console.error = consoleError;
        consoleInstalled = window.console.error === consoleError;
      }
    } catch (_) { }

    return function cleanup() {
      try {
        if (onerrorInstalled && window.onerror === onerror) window.onerror = originalOnerror;
      } catch (_) { }
      try {
        if (consoleInstalled && window.console && window.console.error === consoleError) {
          window.console.error = originalConsoleError;
        }
      } catch (_) { }
    };
  },
  loadConfig(config) {
    window.ct_error_config = config.errorConfig;
  }
};
