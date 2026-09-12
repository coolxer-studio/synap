const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const {
  applyCollectionProfile,
  collectConfiguredValues
} = require('../src/lib/device-info/collection-policy');
const {
  DEFAULT_COLLECTION_PROFILE,
  FULL_COLLECTION_PROFILE,
  normalizeCollectionProfile
} = require('../src/config');

const checker = JSON.parse(fs.readFileSync(
  path.resolve(__dirname, '../../../synap-server/deploy/open_config/checker_config/h5.json'),
  'utf8'
));

function collector(keys) {
  const calls = new Map();
  const entry = {};
  keys.forEach((key) => {
    entry[key] = () => {
      calls.set(key, (calls.get(key) || 0) + 1);
      return `${key}-value`;
    };
  });
  return { entry, calls };
}

function collectSection(section, profile) {
  const keys = checker.deviceConfig[section];
  const { entry, calls } = collector(keys);
  const values = collectConfiguredValues([entry], keys, { section, profile });
  return { keys, values, calls };
}

function valueFor(result, key) {
  return result.values[result.keys.indexOf(key)];
}

test('low profile preserves the deployed DeviceData schema with null placeholders', () => {
  const policy = applyCollectionProfile({ config: checker }, DEFAULT_COLLECTION_PROFILE);
  assert.deepEqual(policy.config.deviceConfig, checker.deviceConfig);
  assert.deepEqual(policy.config.eventConfig.event, ['pageshow', 'pagehide']);

  const network = collectSection('network', DEFAULT_COLLECTION_PROFILE);
  const baseInfo = collectSection('baseInfo', DEFAULT_COLLECTION_PROFILE);
  assert.equal(network.values.length, 3);
  assert.equal(baseInfo.values.length, 23);

  for (const key of ['lanIp']) {
    assert.equal(valueFor(network, key), null, key);
    assert.equal(network.calls.get(key), undefined, key);
  }
  for (const key of ['getCanvasFpCrc', 'getWebglVendorAndRenderer', 'getFonts', 'getWebglFp']) {
    assert.equal(valueFor(baseInfo, key), null, key);
    assert.equal(baseInfo.calls.get(key), undefined, key);
  }
  assert.equal(valueFor(network, 'type'), 'type-value');
  assert.equal(valueFor(baseInfo, 'getPixelRatio'), 'getPixelRatio-value');
});

test('balanced, full, and minimal profiles keep stable positions', () => {
  const profiles = [
    normalizeCollectionProfile('balanced'),
    FULL_COLLECTION_PROFILE,
    normalizeCollectionProfile('minimal')
  ];
  profiles.forEach((profile) => {
    assert.equal(collectSection('network', profile).values.length, checker.deviceConfig.network.length);
    assert.equal(collectSection('baseInfo', profile).values.length, checker.deviceConfig.baseInfo.length);
  });

  const balancedBaseInfo = collectSection('baseInfo', profiles[0]);
  assert.equal(valueFor(balancedBaseInfo, 'getCanvasFpCrc'), 'getCanvasFpCrc-value');
  assert.equal(valueFor(balancedBaseInfo, 'getWebglVendorAndRenderer'), 'getWebglVendorAndRenderer-value');
  assert.equal(valueFor(balancedBaseInfo, 'getFonts'), 'getFonts-value');
  assert.equal(valueFor(balancedBaseInfo, 'getWebglFp'), 'getWebglFp-value');
  assert.equal(valueFor(collectSection('network', profiles[0]), 'lanIp'), null);

  assert.equal(valueFor(collectSection('network', profiles[1]), 'lanIp'), 'lanIp-value');
  assert.equal(valueFor(collectSection('baseInfo', profiles[2]), 'getCanvasFpCrc'), null);
});

test('custom profiles preserve the existing combined WebGL fingerprint semantics', () => {
  const canvasOnly = normalizeCollectionProfile({ canvas: true, webgl: false, fonts: true });
  const canvasResult = collectSection('baseInfo', canvasOnly);
  assert.equal(valueFor(canvasResult, 'getCanvasFpCrc'), 'getCanvasFpCrc-value');
  assert.equal(valueFor(canvasResult, 'getWebglVendorAndRenderer'), null);
  assert.equal(valueFor(canvasResult, 'getWebglFp'), null);
  assert.equal(valueFor(canvasResult, 'getFonts'), 'getFonts-value');

  const webglOnly = normalizeCollectionProfile({ canvas: false, webgl: true });
  const webglResult = collectSection('baseInfo', webglOnly);
  assert.equal(valueFor(webglResult, 'getCanvasFpCrc'), null);
  assert.equal(valueFor(webglResult, 'getWebglVendorAndRenderer'), 'getWebglVendorAndRenderer-value');
  assert.equal(valueFor(webglResult, 'getWebglFp'), null);
});
