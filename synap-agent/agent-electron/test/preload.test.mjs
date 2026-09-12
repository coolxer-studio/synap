import assert from 'node:assert/strict';
import test from 'node:test';
import { installPreloadBridge } from '../src/preload-runtime.js';
import { BRIDGE_NAME, CHANNELS } from '../src/shared.js';

test('preload exposes only the fixed narrow bridge', async () => {
  let exposed;
  const listeners = new Map();
  const ipcRenderer = {
    invoke: async (channel) => channel === CHANNELS.context ? { hostContext: { type: 'electron' }, pendingEvents: [] } : { acknowledged: 1 },
    on: (channel, listener) => listeners.set(channel, listener),
    removeListener: (channel, listener) => { if (listeners.get(channel) === listener) listeners.delete(channel); }
  };
  installPreloadBridge({ contextBridge: { exposeInMainWorld: (name, value) => { exposed = { name, value }; } }, ipcRenderer });
  assert.equal(exposed.name, BRIDGE_NAME);
  assert.deepEqual(Object.keys(exposed.value).sort(), ['ackEvents', 'getContext', 'subscribe', 'unsubscribe']);
  assert.equal('ipcRenderer' in exposed.value, false);
  const context = await exposed.value.getContext();
  assert.equal(Object.isFrozen(context), true);
  let received;
  exposed.value.subscribe((value) => { received = value; });
  listeners.get(CHANNELS.event)({ sender: 'must-not-leak' }, { id: 'event' });
  assert.deepEqual(received, { id: 'event' });
  exposed.value.unsubscribe();
  assert.equal(listeners.size, 0);
});
