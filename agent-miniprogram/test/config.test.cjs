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

test('configuration accepts HTTP without a probe-side host restriction', () => {
  const base = { appKey: '1234567890abcdef1234567890abcdef', appId: 1, appName: 'demo' };
  const options = normalizeOptions(Object.assign({}, base, {
    baseUrl: 'http://example.test:8080/'
  }));
  assert.equal(options.baseUrl, 'http://example.test:8080');
});
