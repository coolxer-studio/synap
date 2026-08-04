const { test, expect } = require('@playwright/test');
const { createH5Codec } = require('@coolxer/secure-communication-js');

const APP_KEY = '1596861234c4ea6ddd041d45b3912345';
const codec = createH5Codec(APP_KEY);
const policy = {
  config: {
    startConfig: [1, '', 10, 30, 10, 60],
    deviceConfig: { baseInfo: [], network: [] },
    selfAppConfig: { base: ['domain'] },
    locationConfig: { base: ['time', 'speed', 'latitude', 'longitude', 'country', 'province', 'city', 'county', 'thoroughfare'] },
    runtimeConfig: { custom: ['format'] },
    debugConfig: { base: ['.*'] },
    errorConfig: { message: ['.*'] },
    eventConfig: { event: ['click'] },
    devToolConfig: { shortCut: ['.*'], windowSise: ['.*'] }
  }
};

async function mockBackend(page, onUpload) {
  await page.route('http://risk.test/**', async (route) => {
    const url = route.request().url();
    if (url.endsWith('/attach/start')) {
      await route.fulfill({ status: 200, body: codec.encrypt(JSON.stringify(policy)) });
    } else if (url.endsWith('/attach/upload')) {
      if (onUpload) await onUpload(route);
      else await route.fulfill({ status: 200, body: codec.encrypt('{"code":0}') });
    } else {
      await route.fulfill({ status: 200, body: codec.encrypt('{"code":0,"data":{"ok":true}}') });
    }
  });
}

test('initializes once, validates user data, queries asynchronously, and destroys cleanly', async ({ page }) => {
  let startRequests = 0;
  await page.route('http://risk.test/**', async (route) => {
    const url = route.request().url();
    if (url.endsWith('/attach/start')) {
      startRequests += 1;
      await route.fulfill({ status: 200, body: codec.encrypt(JSON.stringify(policy)) });
    } else {
      await route.fulfill({ status: 200, body: codec.encrypt('{"code":0,"data":{"ok":true}}') });
    }
  });
  await page.goto('/');
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('running');
  expect(await page.evaluate(() => SynapAgent.init(window.SynapAgentConfig).then(() => SynapAgent.getMetrics().status))).toBe('running');
  expect(startRequests).toBe(1);
  expect(await page.evaluate(() => SynapAgent.userData('user-hash', { scene: 'test' }))).toBe(true);
  expect(await page.evaluate(() => SynapAgent.userData('', {}))).toBe(false);
  expect(await page.evaluate(() => SynapAgent.userData('user', () => {}))).toBe(false);
  expect(await page.evaluate(() => SynapAgent.userData('user', (() => { const value = {}; value.self = value; return value; })()))).toBe(false);
  expect(await page.evaluate(() => SynapAgent.getDeviceInfo())).toEqual({ code: 0, data: { ok: true } });
  await page.evaluate(() => SynapAgent.destroy({ flush: false }));
  expect(await page.evaluate(() => SynapAgent.getMetrics().status)).toBe('destroyed');
  expect(await page.evaluate(() => SynapAgent.userData('user-hash', {}))).toBe(false);
});

test('runs under a strict CSP and with storage disabled', async ({ page }) => {
  await page.addInitScript(() => {
    Storage.prototype.getItem = function () { throw new DOMException('blocked', 'SecurityError'); };
    Storage.prototype.setItem = function () { throw new DOMException('blocked', 'SecurityError'); };
    Storage.prototype.removeItem = function () { throw new DOMException('blocked', 'SecurityError'); };
  });
  await mockBackend(page);
  await page.goto('/csp.html');
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('running');
  expect(await page.evaluate(() => SynapAgent.userData('user-hash', { scene: 'csp' }))).toBe(true);
});

test('restores a failed upload after navigation', async ({ page }) => {
  let failUpload = true;
  const uploadedTypes = [];
  await mockBackend(page, async (route) => {
    if (failUpload) {
      await route.abort('failed');
      return;
    }
    const body = route.request().postData();
    const plaintext = codec.decrypt(body.slice(0, -APP_KEY.length));
    uploadedTypes.push(...JSON.parse(plaintext).data.map((item) => item.type));
    await route.fulfill({ status: 200, body: codec.encrypt('{"code":0}') });
  });
  await page.goto('/');
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('running');
  await page.evaluate(() => SynapAgent.userData('user-hash', { scene: 'restore' }));
  await page.evaluate(() => SynapAgent.flush().catch(() => false));
  failUpload = false;
  await page.reload();
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('running');
  await page.evaluate(() => SynapAgent.flush());
  expect(uploadedTypes).toContain('UserData');
});
