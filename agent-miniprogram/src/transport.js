'use strict';

class TransportError extends Error {
  constructor(type, message, retryable, details) {
    super(message);
    this.name = 'TransportError';
    this.type = type;
    this.retryable = retryable !== false;
    this.details = details || null;
  }
}

class WxTransport {
  constructor(options) {
    this.wx = options.wx;
    this.baseUrl = options.baseUrl;
    this.timeout = options.timeout || 15000;
    this.codec = options.codec;
    this.tasks = new Set();
    this.destroyed = false;
    this.onError = options.onError || function noop() {};
  }

  post(path, payload, code) {
    if (this.destroyed) return Promise.reject(new TransportError('destroyed', 'transport destroyed', false));
    let data;
    try {
      data = this.codec.encodeRequest(JSON.stringify(payload));
    } catch (error) {
      return Promise.reject(new TransportError('encrypt', error.message, false));
    }
    return new Promise((resolve, reject) => {
      let settled = false;
      let timeoutTriggered = false;
      let timeoutId = null;
      const finish = (callback, value) => {
        if (settled) return;
        settled = true;
        if (timeoutId) clearTimeout(timeoutId);
        if (task) this.tasks.delete(task);
        callback(value);
      };
      let task;
      try {
        task = this.wx.request({
          url: this.baseUrl + path,
          method: 'POST',
          timeout: this.timeout,
          header: { 'content-type': 'application/json;charset=UTF-8', code },
          data,
          success: (response) => {
            if (response.statusCode < 200 || response.statusCode >= 300) {
              const retryable = response.statusCode >= 500 || response.statusCode === 408 || response.statusCode === 429;
              finish(reject, new TransportError('http', `HTTP ${response.statusCode}`, retryable, response.statusCode));
              return;
            }
            const body = response.data;
            if (body == null || body === '') {
              finish(reject, new TransportError('empty', 'empty response'));
              return;
            }
            try {
              const plaintext = this.codec.decodeResponse(typeof body === 'string' ? body : String(body));
              const parsed = JSON.parse(plaintext);
              if (!parsed || Number(parsed.status) !== 1) {
                finish(reject, new TransportError('business', parsed && parsed.msg || 'business failure', true, parsed));
                return;
              }
              finish(resolve, parsed);
            } catch (error) {
              const type = error instanceof SyntaxError ? 'json' : 'decrypt';
              finish(reject, new TransportError(type, error.message));
            }
          },
          fail: (error) => {
            const message = String(error && error.errMsg || error || 'network failure');
            const timedOut = timeoutTriggered || /timeout/i.test(message);
            finish(reject, new TransportError(timedOut ? 'timeout' : 'network', message));
          }
        });
        if (task && !settled) this.tasks.add(task);
      } catch (error) {
        finish(reject, new TransportError('network', error.message));
      }
      if (settled) return;
      timeoutId = setTimeout(() => {
        if (settled) return;
        timeoutTriggered = true;
        try { if (task && typeof task.abort === 'function') task.abort(); } catch (_) { /* best effort */ }
        finish(reject, new TransportError('timeout', `request timed out after ${this.timeout}ms`));
      }, this.timeout + 50);
    }).catch((error) => {
      this.onError(error, path);
      throw error;
    });
  }

  destroy() {
    this.destroyed = true;
    this.tasks.forEach((task) => {
      try { if (task && typeof task.abort === 'function') task.abort(); } catch (_) { /* best effort */ }
    });
    this.tasks.clear();
  }
}

module.exports = { WxTransport, TransportError };
