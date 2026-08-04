const test = require('node:test');
const assert = require('node:assert/strict');

class FakeXMLHttpRequest {
  constructor() {
    this.headers = {};
    this.status = 0;
    this.readyState = 0;
  }
  open(method, url, async = true) {
    this.method = method;
    this.url = url;
    this.async = async;
  }
  setRequestHeader(key, value) { this.headers[key] = value; }
  send(data) {
    this.data = data;
    if (this.url.includes('timeout')) {
      setTimeout(() => this.ontimeout && this.ontimeout(), 1);
      return;
    }
    const complete = () => {
      this.readyState = 4;
      this.status = this.url.includes('failure') ? 503 : 200;
      this.responseText = `response:${this.url}`;
      if (this.onload) this.onload();
    };
    if (this.async === false) complete();
    else setTimeout(complete, this.url.includes('slow') ? 10 : 1);
  }
}

global.window = { XMLHttpRequest: FakeXMLHttpRequest };
const http = require('../src/lib/secure-communication/ajax-http').default;

test('concurrent requests keep independent response state', async () => {
  const [slow, fast] = await Promise.all([
    http.post('/slow', 'a'),
    http.post('/fast', 'b')
  ]);
  assert.equal(slow, 'response:/slow');
  assert.equal(fast, 'response:/fast');
});

test('HTTP errors include status and category', async () => {
  await assert.rejects(http.post('/failure', 'x'), (error) => error.type === 'http' && error.status === 503);
});

test('timeouts are reported separately from network and HTTP errors', async () => {
  await assert.rejects(http.post('/timeout', 'x'), (error) => error.type === 'timeout' && error.status === 0);
});

test('legacy synchronous transport returns before the caller continues', () => {
  assert.equal(http.requestSync('POST', '/sync', { data: 'x' }), 'response:/sync');
});
