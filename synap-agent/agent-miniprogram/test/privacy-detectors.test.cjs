'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { pathTemplate, sanitizeText, sanitizeData, errorTemplate } = require('../src/privacy');
const { detectFormatting, BehaviorMonitor } = require('../src/detectors');

test('paths and errors discard query, fragment, token, phone and email', () => {
  assert.equal(pathTemplate('https://api.test/users/12345?token=secret#x'), '/users/:id');
  const clean = errorTemplate('token=abc 13800138000 user@example.com');
  assert.equal(clean.includes('abc'), false);
  assert.equal(clean.includes('13800138000'), false);
  assert.equal(clean.includes('user@example.com'), false);
  assert.match(sanitizeText('password=secret'), /\[masked\]/);
  assert.equal(errorTemplate('boom\n at https://example.test/app.js?token=x:10').includes('app.js'), false);
  const user = JSON.stringify(sanitizeData({ note: 'call 13800138000 or user@example.com' }, 0));
  assert.equal(user.includes('13800138000'), false);
  assert.equal(user.includes('user@example.com'), false);
});

test('code formatting detector exposes an explicit binary label', () => {
  assert.equal(detectFormatting('function f(){return 1}'), 0);
  assert.equal(detectFormatting('function f(){\n return 1;\n}'), 1);
});

test('interaction aggregation never carries input value or dataset', () => {
  const emitted = [];
  const monitor = new BehaviorMonitor((fact) => emitted.push(fact));
  monitor.track({ type: 'input', timeStamp: 10, detail: { value: 'secret' }, currentTarget: { dataset: { token: 'hidden' } } });
  monitor.track({ type: 'tap', timeStamp: 20, detail: { x: 1, y: 2 }, target: {} });
  const fact = monitor.flush();
  const serialized = JSON.stringify(fact);
  assert.equal(serialized.includes('secret'), false);
  assert.equal(serialized.includes('hidden'), false);
  assert.equal(fact.data.inputCount, 1);
});
