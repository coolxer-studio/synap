'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { DeviceCollector, isPrivateAddress } = require('../src/device');

function mockWx() {
  return {
    getDeviceInfo: () => ({ brand: 'phone', model: 'm', system: 'iOS 17.0', platform: 'ios' }),
    getWindowInfo: () => ({ pixelRatio: 3, screenHeight: 800, screenWidth: 400 }),
    getSystemSetting: () => ({}),
    getAppBaseInfo: () => ({ SDKVersion: '3.0.0' }),
    getBatteryInfoSync: () => ({ level: 80, isCharging: false }),
    getAccountInfoSync: () => ({ miniProgram: { appId: 'wx-test', version: '1.2.3', envVersion: 'release' } }),
    getLaunchOptionsSync: () => ({ scene: 1001 }),
    getNetworkType: ({ success }) => success({ networkType: 'wifi' })
  };
}

test('common has exactly 24 positions and no fake IP placeholders', async () => {
  const storageValues = new Map();
  const collector = new DeviceCollector({
    wx: mockWx(),
    storage: { get: (key) => storageValues.get(key), set: (key, value) => storageValues.set(key, value) },
    options: { appId: 1, appName: 'demo', capabilities: { permission: false, localAddress: false, location: false } }
  });
  await collector.prepare();
  const common = collector.common('opaque');
  assert.equal(common.length, 24);
  assert.equal(common[6], 'wx-test');
  assert.equal(common[7], '1.2.3');
  assert.equal(common[14], '');
  assert.equal(common[15], '');
  assert.equal(common.includes('0.0.0.0'), false);
});

test('only actual private addresses are accepted', () => {
  assert.equal(isPrivateAddress('192.168.1.2'), true);
  assert.equal(isPrivateAddress('172.31.0.1'), true);
  assert.equal(isPrivateAddress('8.8.8.8'), false);
  assert.equal(isPrivateAddress('0.0.0.0'), false);
});
