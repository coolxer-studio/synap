const test = require('node:test');
const assert = require('node:assert/strict');
const MessageQueue = require('../src/lib/message-queue').default;

function memoryStorage(initial = {}) {
  const values = new Map(Object.entries(initial));
  return {
    getItem: (key) => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key)
  };
}

test('enqueue stores immutable snapshots and tracks overflow', () => {
  const queue = new MessageQueue({ limit: 2 });
  const message = { type: 'EventData', data: { x: 1 } };
  assert.equal(queue.enqueue(message), true);
  message.data.x = 9;
  assert.equal(queue.items[0].data.x, 1);
  assert.equal(queue.enqueue({ type: 'DeviceData' }), true);
  assert.equal(queue.enqueue({ type: 'RuntimeData' }), false);
  assert.deepEqual(queue.metrics(), { queued: 2, dropped: 1 });
});

test('failed batches are restored before messages queued during upload', () => {
  const queue = new MessageQueue({ limit: 4 });
  ['a', 'b', 'c'].forEach((type) => queue.enqueue({ type }));
  const batch = queue.takeBatch(2);
  queue.enqueue({ type: 'd' });
  queue.restoreBatch(batch);
  assert.deepEqual(queue.items.map((item) => item.type), ['a', 'b', 'c', 'd']);
});

test('pending messages survive a page lifecycle', () => {
  const storage = memoryStorage();
  const first = new MessageQueue({ storage, storageKey: 'pending', limit: 5 });
  first.enqueue({ type: 'EventData', data: { type: 'click' } });
  assert.equal(first.persist(), true);
  const restored = new MessageQueue({ storage, storageKey: 'pending', limit: 5 });
  assert.equal(restored.items.length, 1);
  assert.equal(restored.items[0].data.type, 'click');
  assert.equal(storage.getItem('pending'), null);
});

