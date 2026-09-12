const { test, expect } = require('@playwright/test');

const secureError = JSON.stringify({
  code: 'SC_HANDSHAKE_FAILED',
  message: 'Secure handshake failed',
  traceId: 'e2e-trace'
});

async function rejectHandshake(page, observed) {
  await page.route('https://risk.test/**', async (route) => {
    const request = route.request();
    observed.push({
      path: new URL(request.url()).pathname,
      method: request.method(),
      headers: request.headers(),
      body: request.postDataJSON()
    });
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: secureError
    });
  });
}

test('missing public bootstrap config exposes the API without startup errors', async ({ page }) => {
  const consoleErrors = [];
  const requests = [];
  page.on('console', (message) => {
    if (message.type() === 'error' || message.type() === 'warning') {
      consoleErrors.push(message.text());
    }
  });
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/sc/v1/')) requests.push(request.url());
  });

  await page.goto('/unconfigured.html');
  await page.waitForTimeout(100);

  expect(await page.evaluate(() => typeof window.SynapAgent)).toBe('object');
  expect(await page.evaluate(() => window.SynapAgent.getMetrics().status)).toBe('idle');
  expect(consoleErrors).toEqual([]);
  expect(requests).toEqual([]);
});

test('uses only the protocol v1 handshake entry and fails closed', async ({ page }) => {
  const observed = [];
  await rejectHandshake(page, observed);
  await page.goto('/');
  await expect.poll(() => observed.length).toBe(1);

  expect(observed[0].path).toBe('/sc/v1/handshake');
  expect(observed[0].method).toBe('POST');
  expect(observed[0].headers.code).toBeUndefined();
  expect(observed[0].body.v).toBe(1);
  expect(observed[0].body.suite).toBe('P256_HKDF_SHA256_AES256_GCM');
  expect(observed[0].body.deviceType).toBe('H5');
  expect(observed.some((request) => request.path === '/sc/v1/message')).toBe(false);
  await expect.poll(() => page.evaluate(() => SynapAgent.getMetrics().status)).toBe('idle');
});

test('does not expose keys, codecs, or legacy upload globals', async ({ page }) => {
  const observed = [];
  await rejectHandshake(page, observed);
  await page.goto('/csp.html');
  await expect.poll(() => observed.length).toBe(1);

  const globals = await page.evaluate(() => ({
    userData: typeof window.userData,
    codec: typeof window.codec,
    key: typeof window.appKey,
    secureClient: typeof window.secureClient,
    runtime: typeof window.__synapAgentRuntime,
    policy: typeof window._polic,
    queue: typeof window.probeMsgQueue,
    memory: typeof window.esl,
    agent: typeof window.SynapAgent
  }));
  expect(globals).toEqual({
    userData: 'undefined',
    codec: 'undefined',
    key: 'undefined',
    secureClient: 'undefined',
    runtime: 'undefined',
    policy: 'undefined',
    queue: 'undefined',
    memory: 'undefined',
    agent: 'object'
  });
});
