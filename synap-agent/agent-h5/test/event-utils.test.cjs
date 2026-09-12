const test = require('node:test');
const assert = require('node:assert/strict');

global.document = { body: null };
const { getTouchXY } = require('../src/lib/user-behavior/event-utils');

test('touch coordinates always use an object shape', () => {
  assert.deepEqual(getTouchXY({ changedTouches: [{ pageX: 12, pageY: 34 }] }), { x: 12, y: 34 });
  assert.deepEqual(
    getTouchXY({ changedTouches: [{ clientX: 10, clientY: 20 }] }, { scrollLeft: 3, scrollTop: 4, clientLeft: 1, clientTop: 2 }),
    { x: 12, y: 22 }
  );
  assert.deepEqual(getTouchXY({ changedTouches: [] }), { x: 0, y: 0 });
});

