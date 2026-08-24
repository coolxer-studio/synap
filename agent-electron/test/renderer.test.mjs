import assert from 'node:assert/strict';
import test from 'node:test';
import { createRendererRuntime } from '../src/renderer-runtime.js';

function fixture({ protocol = 'synap-electron:', packaged = true, baseUrl = 'https://synap.example.com' } = {}) {
  const acknowledged = [];
  let callback;
  const hostContext = { type: 'electron', schemaVersion: 1, app: { packaged }, security: {} };
  const pending = { id: 'electron-event-1000-1', name: 'unresponsive', observedAt: 1001, data: { reason: 'test' } };
  const bridge = {
    getContext: async () => ({ hostContext, pendingEvents: [pending] }),
    ackEvents: async (ids) => { acknowledged.push(...ids); return { acknowledged: ids.length }; },
    subscribe: (value) => { callback = value; return true; },
    unsubscribe: () => { callback = null; }
  };
  const calls = { init: [], destroy: [], events: [] };
  const agent = {
    version: '1.3.0',
    init: async (options) => { calls.init.push(options); return agent; },
    destroy: async (options) => { calls.destroy.push(options); },
    recordEvent: (name, data) => { calls.events.push([name, data]); return true; }
  };
  const currentWindow = { location: { protocol }, SynapElectronProbe: bridge };
  const runtime = createRendererRuntime({ getWindow: () => currentWindow, getAgent: () => agent });
  return { runtime, calls, acknowledged, baseUrl, emit: (event) => callback(event) };
}

test('injects host context and acknowledges persisted events', async () => {
  const value = fixture();
  const agent = await value.runtime.initElectronAgent({ baseUrl: value.baseUrl, appId: 'desktop', serverTrustAnchors: { key: 'public' } });
  assert.equal(agent.version, '1.3.0');
  assert.equal(value.calls.init[0].hostContext.type, 'electron');
  assert.equal(value.calls.events[0][0], 'unresponsive');
  assert.deepEqual(value.acknowledged, ['electron-event-1000-1']);
  await value.runtime.destroyElectronAgent();
  assert.deepEqual(value.calls.destroy[0], { flush: true });
});

test('fails closed without a secure renderer or preload bridge', async () => {
  const fileValue = fixture({ protocol: 'file:' });
  await assert.rejects(() => fileValue.runtime.initElectronAgent({ baseUrl: fileValue.baseUrl }), /secure custom-protocol/);
  const value = fixture();
  const noBridge = createRendererRuntime({ getWindow: () => ({ location: { protocol: 'https:' } }), getAgent: () => ({}) });
  await assert.rejects(() => noBridge.initElectronAgent({ baseUrl: value.baseUrl }), /bridge is unavailable/);
});

test('allows loopback HTTP only for an unpackaged application', async () => {
  const development = fixture({ packaged: false, baseUrl: 'http://127.0.0.1:11099' });
  await development.runtime.initElectronAgent({ baseUrl: development.baseUrl, appId: 'desktop', serverTrustAnchors: { key: 'public' } });
  const packaged = fixture({ packaged: true, baseUrl: 'http://127.0.0.1:11099' });
  await assert.rejects(() => packaged.runtime.initElectronAgent({ baseUrl: packaged.baseUrl }), /must use an HTTPS/);
});
