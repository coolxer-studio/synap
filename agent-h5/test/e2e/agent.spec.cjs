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

function decodeUpload(route) {
  const body = route.request().postData();
  return JSON.parse(codec.decrypt(body.slice(0, -APP_KEY.length)));
}

async function acknowledge(route, request = decodeUpload(route), overrides = {}) {
  const acceptedMessageIds = request.data.map((item) => item.messageId);
  const response = {
    status: 1,
    ack: {
      batchId: request.meta.batchId,
      acceptedMessageIds,
      duplicateMessageIds: [],
      rejectedItems: [],
      ...overrides
    }
  };
  await route.fulfill({ status: 200, body: codec.encrypt(JSON.stringify(response)) });
}

async function mockBackend(page, onUpload) {
  await page.route('http://risk.test/**', async (route) => {
    const url = route.request().url();
    if (url.endsWith('/attach/start')) {
      await route.fulfill({ status: 200, body: codec.encrypt(JSON.stringify(policy)) });
    } else if (url.endsWith('/attach/upload')) {
      if (onUpload) await onUpload(route);
      else await acknowledge(route);
    } else {
      await route.fulfill({ status: 200, body: codec.encrypt('{"code":0,"data":{"ok":true}}') });
    }
  });
}

test('initializes once, validates user data, queries asynchronously, and destroys cleanly', async ({ page }) => {
  let startRequests = 0;
  let startCommon;
  await page.route('http://risk.test/**', async (route) => {
    const url = route.request().url();
    if (url.endsWith('/attach/start')) {
      startRequests += 1;
      startCommon = decodeUpload(route).common;
      await route.fulfill({ status: 200, body: codec.encrypt(JSON.stringify(policy)) });
    } else {
      await route.fulfill({ status: 200, body: codec.encrypt('{"code":0,"data":{"ok":true}}') });
    }
  });
  await page.goto('/');
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('running');
  expect(startCommon[14]).not.toBe('0.0.0.0');
  expect(startCommon[15]).toBe('');
  expect(await page.evaluate(() => SynapAgent.getMetrics().networkInfo.status))
    .toMatch(/^(available|masked|unavailable|disabled)$/);
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
    const request = decodeUpload(route);
    uploadedTypes.push(...request.data.map((item) => item.type));
    await acknowledge(route, request);
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

test('keeps stable message ids across invalid acknowledgements and retries', async ({ page }) => {
  const attempts = [];
  let invalidAck = true;
  await mockBackend(page, async (route) => {
    const request = decodeUpload(route);
    attempts.push(request.data.map((item) => item.messageId));
    if (invalidAck) {
      invalidAck = false;
      await route.fulfill({ status: 200, body: codec.encrypt(JSON.stringify({ status: 0 })) });
      return;
    }
    await acknowledge(route, request);
  });
  await page.goto('/');
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('running');
  await page.evaluate(() => SynapAgent.userData('user-hash', { scene: 'ack' }));
  await page.evaluate(() => SynapAgent.flush().catch(() => false));
  await page.evaluate(() => SynapAgent.flush());
  expect(attempts.length).toBe(2);
  expect(attempts[1]).toEqual(attempts[0]);
  expect(await page.evaluate(() => SynapAgent.getMetrics().ackFailures)).toBe(1);
});

test('reports static facts once for the same page session across reloads', async ({ page }) => {
  const uploadedTypes = [];
  await mockBackend(page, async (route) => {
    const request = decodeUpload(route);
    uploadedTypes.push(...request.data.map((item) => item.type));
    await acknowledge(route, request);
  });
  await page.goto('/');
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('running');
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().queue.queued)).toBeGreaterThan(0);
  await page.evaluate(() => SynapAgent.flush());
  await page.reload();
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('running');
  await page.evaluate(() => SynapAgent.userData('user-hash', { scene: 'reload' }));
  await page.evaluate(() => SynapAgent.flush());

  expect(uploadedTypes.filter((type) => type === 'DeviceData')).toHaveLength(1);
  expect(uploadedTypes.filter((type) => type === 'SelfAppData')).toHaveLength(1);
  expect(uploadedTypes.filter((type) => type === 'RuntimeData')).toHaveLength(1);
});
