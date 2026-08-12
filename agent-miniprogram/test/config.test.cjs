'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { ENDPOINTS, PROFILES, normalizeOptions } = require('../src/config');

test('v1 publishes no Mini Program wire endpoints', () => {
  assert.deepEqual(Object.values(ENDPOINTS), []);
});

test('low is non-permission profile and debug is enabled', () => {
  assert.equal(PROFILES.low.debug, true);
  assert.equal(PROFILES.low.location, false);
  assert.equal(PROFILES.low.localAddress, false);
  assert.equal(PROFILES.low.permission, false);
});

test('configuration requires HTTPS except explicit localhost tests', () => {
  const base = { appKey: '1234567890abcdef1234567890abcdef', appId: 1, appName: 'demo' };
  assert.throws(() => normalizeOptions(Object.assign({}, base, { baseUrl: 'http://example.test' })), /HTTPS/);
  const options = normalizeOptions(Object.assign({}, base, {
    baseUrl: 'http://127.0.0.1:8080/',
    allowInsecureForTesting: true
  }));
  assert.equal(options.baseUrl, 'http://127.0.0.1:8080');
});
