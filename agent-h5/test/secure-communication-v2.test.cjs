const test = require('node:test');
const assert = require('node:assert/strict');

const {
  IndexedDbIdentityStore,
  SecureClientConfig,
  SecureRequest
} = require('@coolxer/secure-communication-js');

test('secure communication uses the 2.0 request and identity contracts', () => {
  const identityStore = new IndexedDbIdentityStore({
    databaseName: 'coolxer-secure-communication-v2'
  });
  const config = new SecureClientConfig({
    baseUrl: 'http://127.0.0.1:11099',
    appId: 'synap-h5-test',
    deviceType: 'H5',
    serverTrustAnchors: { 'server-key-test': 'anchor' },
    identityStore,
    requestTimeoutMillis: 1234,
    allowInsecureLoopbackForTesting: true,
    fetch: async () => { throw new Error('not called'); }
  });
  const request = new SecureRequest({
    method: 'POST',
    logicalPath: '/attach/upload',
    contentType: 'application/json',
    protectedHeaders: { code: 'h5-test' },
    body: '{}',
    requestId: 'request-1'
  });

  assert.equal(config.identityStore.databaseName, 'coolxer-secure-communication-v2');
  assert.equal(config.requestTimeoutMillis, 1234);
  assert.equal(config.allowInsecureLoopbackForTesting, true);
  assert.equal(request.logicalPath, '/attach/upload');
  assert.equal(request.protectedHeaders.code, 'h5-test');
  assert.equal(request.requestId, 'request-1');
});
