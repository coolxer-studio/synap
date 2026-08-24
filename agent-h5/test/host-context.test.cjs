const assert = require('node:assert/strict');
const test = require('node:test');
const {
  attachHostCapabilities,
  createAdapterEventFact,
  electronIntegrityEvidence,
  normalizeAdapterEvent,
  normalizeHostContext,
  pageUrlTemplate
} = require('../src/lib/host-context').default || require('../src/lib/host-context');

test('normalizes an Electron host context without changing browser defaults', () => {
  assert.equal(normalizeHostContext(null), null);
  const value = normalizeHostContext({ type: 'electron', schemaVersion: 1, app: { name: 'Demo', packaged: true }, security: { sandbox: true } });
  assert.equal(value.type, 'electron');
  assert.equal(value.security.sandbox, true);
});

test('rejects sensitive, oversized, and malformed adapter data', () => {
  assert.throws(() => normalizeHostContext({ type: 'browser', schemaVersion: 1 }), /Electron schemaVersion 1/);
  assert.throws(() => normalizeHostContext({ type: 'electron', schemaVersion: 1, token: 'secret' }), /sensitive key/);
  assert.throws(() => normalizeAdapterEvent('bad event name', {}), /event name/);
  assert.throws(() => normalizeAdapterEvent('electron.event', { password: 'secret' }), /sensitive key/);
  const oversized = Object.fromEntries(Array.from({ length: 32 }, (_, index) => [`value${index}`, 'x'.repeat(512)]));
  assert.throws(() => normalizeAdapterEvent('electron.event', oversized), /maximum byte size/);
});

test('removes query, hash, and identifier values from page templates', () => {
  assert.equal(pageUrlTemplate('https://app.example/orders/12345?token=secret#detail'), 'https://app.example/orders/:id');
});

test('adds Electron capabilities and integrity evidence only for an Electron host', () => {
  const context = {
    type: 'electron', schemaVersion: 1, app: { packaged: true },
    security: { nodeIntegration: true, contextIsolation: false, sandbox: false, webSecurity: true, devToolsOpened: true }
  };
  assert.equal(attachHostCapabilities({ battery: {} }, null).electron, undefined);
  assert.equal(attachHostCapabilities({ battery: {} }, context).electron, context);
  const evidence = electronIntegrityEvidence(context);
  assert.equal(evidence.signals.electronNodeIntegrationEnabled, true);
  assert.equal(evidence.signals.electronWebSecurityDisabled, false);
  assert.equal(evidence.weights.electronContextIsolationDisabled, 5);
});

test('creates a server-compatible EventData adapter fact', () => {
  const fact = createAdapterEventFact('render-process-gone', { reason: 'crashed' }, {
    buildDate: '2026-08-24 12:00:00', href: 'synap-electron://app/orders/12345?secret=1',
    docTitle: 'Desktop', online: true
  });
  assert.equal(fact.type, 'EventData');
  assert.equal(fact.href, 'synap-electron://app/orders/:id');
  assert.deepEqual(fact.data, { source: 'host-adapter', trusted: true, name: 'render-process-gone', detail: { reason: 'crashed' } });
});
