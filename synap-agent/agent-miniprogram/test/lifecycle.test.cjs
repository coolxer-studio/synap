'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const SynapAgent = require('../src');

test('protocol v1 rejects initialization before any network request', async () => {
  let requests = 0;
  global.wx = { request() { requests += 1; } };
  await assert.rejects(
    SynapAgent.init({}),
    /does not support WeChat Mini Program/
  );
  assert.equal(requests, 0);
  delete global.wx;
});
