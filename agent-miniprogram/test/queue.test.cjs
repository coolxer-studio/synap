'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { MessageQueue } = require('../src/message-queue');

function item(id, sessionId) {
  return {
    sessionId,
    common: new Array(24).fill(''),
    headerCode: '00000000-0000-4000-8000-000000000000-wechat-1-1.0.0',
    message: { type: 'RuntimeData', messageId: id, sequence: 1, observedAt: 1 }
  };
}

test('batch is atomic and concurrent enqueue survives partial ACK', () => {
  const queue = new MessageQueue({ limit: 50, batchLimit: 50 });
  queue.enqueue(item('a', 'old'));
  queue.enqueue(item('b', 'old'));
  const batch = queue.takeBatch();
  queue.enqueue(item('c', 'new'));
  assert.deepEqual(batch.map((entry) => entry.message.messageId), ['a', 'b']);
  const result = queue.complete(['a'], [], [{ messageId: 'b', retryable: true }]);
  assert.deepEqual(result, { confirmed: 1, restored: 1, rejected: 0 });
  assert.deepEqual(queue.items.map((entry) => entry.message.messageId), ['b', 'c']);
});

test('batch never mixes persisted and current session common snapshots', () => {
  const queue = new MessageQueue({ limit: 50, batchLimit: 50 });
  queue.enqueue(item('old', '100'));
  queue.enqueue(item('new', '200'));
  assert.deepEqual(queue.takeBatch().map((entry) => entry.message.messageId), ['old']);
});

test('the total of queued and in-flight messages never exceeds capacity', () => {
  let dropped = 0;
  const queue = new MessageQueue({ limit: 2, batchLimit: 2, onDrop: () => { dropped += 1; } });
  queue.enqueue(item('1', 's'));
  queue.enqueue(item('2', 's'));
  queue.takeBatch();
  assert.equal(queue.enqueue(item('3', 's')), null);
  assert.equal(queue.size(), 2);
  assert.equal(dropped, 1);
});

test('capacity and encrypted persistence are bounded', () => {
  const memory = new Map();
  const storage = { get: (key) => memory.get(key), set: (key, value) => { memory.set(key, value); return true; }, remove: (key) => memory.delete(key) };
  const codec = { encrypt: (value) => `x${value}`, decrypt: (value) => value.slice(1) };
  let dropped = 0;
  const queue = new MessageQueue({ limit: 2, batchLimit: 2, storage, storageKey: 'pending', codec, onDrop: () => { dropped += 1; } });
  queue.enqueue(item('1', 's'));
  queue.enqueue(item('2', 's'));
  queue.enqueue(item('3', 's'));
  assert.equal(dropped, 1);
  assert.equal(queue.persist(100), true);
  const restored = new MessageQueue({ limit: 2, batchLimit: 2, storage, storageKey: 'pending', codec });
  assert.equal(restored.restore(200, 1000), 2);
  assert.deepEqual(restored.items.map((entry) => entry.message.messageId), ['2', '3']);
});

test('corrupt persisted queue fails open and is cleared', () => {
  const memory = new Map([['pending', 'bad']]);
  let reason = '';
  const queue = new MessageQueue({
    limit: 2,
    storage: { get: (key) => memory.get(key), set: () => true, remove: (key) => memory.delete(key) },
    storageKey: 'pending',
    codec: { decrypt: () => { throw new Error('bad ciphertext'); } },
    onDrop: (value) => { reason = value; }
  });
  assert.equal(queue.restore(Date.now(), 1000), 0);
  assert.equal(reason, 'corrupt_storage');
  assert.equal(memory.has('pending'), false);
});
