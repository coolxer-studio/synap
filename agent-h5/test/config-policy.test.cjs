const test = require('node:test');
const assert = require('node:assert/strict');

global.window = { location: { protocol: 'http:' } };
const fs = require('node:fs');
const path = require('node:path');
const {
  COMMON_FIELD_COUNT, ENDPOINT_PATHS, resolveOptions, createEndpoints,
  hasSecurityBootstrap, isLoopbackHttpUrl, normalizeBaseUrl, policyCacheKey
} = require('../src/config');
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
  const options = resolveOptions({ appId: 'app-1', baseUrl: 'https://risk.example.test/', serverTrustAnchors: { key1: 'anchor' }, collectionProfile: 'minimal' });
  assert.equal(options.baseUrl, 'https://risk.example.test');
  assert.equal(options.collectionProfile.canvas, false);
  assert.equal(createEndpoints(options.baseUrl).upload, '/attach/upload');
  assert.deepEqual(Object.keys(ENDPOINT_PATHS), ['start', 'upload', 'deviceInfo', 'rating', 'label']);
  assert.equal(JSON.stringify(ENDPOINT_PATHS).includes('/secure/v2'), false);
  assert.equal(JSON.stringify(ENDPOINT_PATHS).includes('/security/reports'), false);
  assert.match(policyCacheKey('key'), /key$/);
  assert.notEqual(policyCacheKey('key-a'), policyCacheKey('key-b'));
});

test('default profile is low intrusion and high entropy collection is explicit', () => {
  const defaults = resolveOptions({ appId: 'app-1', baseUrl: 'https://risk.example.test', serverTrustAnchors: { key1: 'anchor' } });
  for (const capability of ['canvas', 'webgl', 'fonts', 'webrtc', 'location', 'network']) {
    assert.equal(defaults.collectionProfile[capability], false, capability);
  }
  assert.equal(defaults.collectionProfile.userBehavior, true);
  assert.equal(defaults.collectionProfile.integrity, true);
  assert.equal(defaults.collectionProfile.debug, true);
  assert.equal(resolveOptions({ appId: 'app-1', baseUrl: 'https://risk.example.test', serverTrustAnchors: { key1: 'anchor' }, collectionProfile: 'minimal' }).collectionProfile.debug, false);
  assert.equal(resolveOptions({ appId: 'app-1', baseUrl: 'https://risk.example.test', serverTrustAnchors: { key1: 'anchor' }, collectionProfile: 'full' }).collectionProfile.canvas, true);
});

test('HTTPS pages reject mixed-content endpoints', () => {
  window.location.protocol = 'https:';
  assert.throws(() => resolveOptions({ appId: 'app-1', serverTrustAnchors: { key1: 'anchor' }, baseUrl: 'http://risk.example.test' }), /insecure HTTP/);
  window.location.protocol = 'http:';
});

test('HTTP is limited to loopback development endpoints', () => {
  assert.equal(isLoopbackHttpUrl('http://127.0.0.1:11099'), true);
  assert.equal(isLoopbackHttpUrl('http://127.10.20.30:11099'), true);
  assert.equal(isLoopbackHttpUrl('http://localhost:11099'), true);
  assert.equal(isLoopbackHttpUrl('http://[::1]:11099'), true);
  assert.equal(isLoopbackHttpUrl('https://127.0.0.1:11099'), false);
  assert.equal(normalizeBaseUrl('http://127.0.0.1:11099/'), 'http://127.0.0.1:11099');
  assert.throws(() => normalizeBaseUrl('http://risk.example.test'), /must use HTTPS/);
});

test('auto initialization requires appId and a server trust anchor', () => {
  assert.equal(hasSecurityBootstrap({}), false);
  assert.equal(hasSecurityBootstrap({ appId: 'app-1' }), false);
  assert.equal(hasSecurityBootstrap({ serverTrustAnchors: { key1: 'anchor' } }), false);
  assert.equal(hasSecurityBootstrap({
    appId: 'app-1', serverTrustAnchors: { key1: 'anchor' }
  }), true);
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
