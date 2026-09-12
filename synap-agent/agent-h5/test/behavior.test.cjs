const test = require('node:test');
const assert = require('node:assert/strict');

test('behavior monitor aggregates trusted state and supports action windows without input values', () => {
  const fakeWindow = new EventTarget();
  fakeWindow.requestAnimationFrame = (callback) => { callback(performance.now()); return 1; };
  fakeWindow.cancelAnimationFrame = () => {};
  global.window = fakeWindow;
  global.document = new EventTarget();
  document.visibilityState = 'visible';
  global.navigator = { userActivation: { isActive: true, hasBeenActive: true } };
  const monitor = require('../src/lib/user-behavior/behavior-monitor').default;
  const messages = [];
  const controller = monitor((message) => messages.push(message), 100000);
  assert.equal(controller.markAction({ id: 'random-action-1', name: 'login', phase: 'start' }), true);
  const pointer = new Event('pointerdown');
  Object.defineProperties(pointer, { clientX: { value: 10 }, clientY: { value: 20 }, pointerType: { value: 'mouse' } });
  window.dispatchEvent(pointer);
  window.dispatchEvent(new Event('click'));
  window.dispatchEvent(new Event('input'));
  assert.equal(controller.markAction({ id: 'random-action-1', name: 'login', phase: 'success' }), true);
  const action = messages.find((message) => message.type === 'ActionData');
  assert.ok(action);
  assert.equal(action.data.name, 'login');
  assert.equal(action.data.behavior.inputCount, 1);
  assert.equal(JSON.stringify(action).includes('password'), false);
  controller();
});
