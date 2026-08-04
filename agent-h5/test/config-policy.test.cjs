const test = require('node:test');
const assert = require('node:assert/strict');

global.window = { location: { protocol: 'http:' } };
const fs = require('node:fs');
const path = require('node:path');
const { COMMON_FIELD_COUNT, resolveOptions, createEndpoints, policyCacheKey } = require('../src/config');
const { validatePolicy, decodePolicy } = require('../src/lib/policy');

function validPolicy() {
  return {
    config: {
      startConfig: [1, './core.js', 10, 30, 10, 60],
      deviceConfig: {}, selfAppConfig: {}, locationConfig: {}, runtimeConfig: {},
      debugConfig: {}, errorConfig: {}, eventConfig: {}, devToolConfig: {}
    }
  };
}

test('configuration resolves profiles and endpoints without obfuscation', () => {
  const options = resolveOptions({ appKey: 'key', baseUrl: 'https://risk.example.test/', collectionProfile: 'minimal' });
  assert.equal(options.baseUrl, 'https://risk.example.test');
  assert.equal(options.collectionProfile.canvas, false);
  assert.equal(createEndpoints(options.baseUrl).upload, 'https://risk.example.test/sc/h5/attach/upload');
  assert.match(policyCacheKey('key'), /key$/);
  assert.notEqual(policyCacheKey('key-a'), policyCacheKey('key-b'));
});

test('HTTPS pages reject mixed-content endpoints', () => {
  window.location.protocol = 'https:';
  assert.throws(() => resolveOptions({ baseUrl: 'http://risk.example.test' }), /insecure HTTP/);
  window.location.protocol = 'http:';
});

test('policy validation rejects incomplete start configuration', () => {
  const policy = validPolicy();
  policy.config.startConfig = [1, 2];
  assert.throws(() => validatePolicy(policy), /startConfig/);
});

test('policy decode distinguishes invalid JSON', () => {
  const codec = { decodeResponse: (value) => value };
  assert.deepEqual(decodePolicy(codec, JSON.stringify(validPolicy())), validPolicy());
  assert.throws(() => decodePolicy(codec, '{'), /Unable to parse/);
});

test('client contract accepts the deployed H5 checker and keeps 24 common fields', () => {
  const deployed = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../../synap-server/deploy/open_config/checker_config/h5.json'), 'utf8'));
  assert.equal(COMMON_FIELD_COUNT, 24);
  assert.equal(validatePolicy({ config: deployed }).config.deviceConfig.baseInfo.length > 0, true);
});
