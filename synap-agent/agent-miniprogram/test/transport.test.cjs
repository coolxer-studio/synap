'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { WxTransport } = require('../src/transport');

const codec = {
  encodeRequest: (value) => `request:${value}`,
  decodeResponse: (value) => value.replace(/^response:/, '')
};

test('each request owns a task and validates business response', async () => {
  let seenHeader;
  const wx = { request(options) {
    seenHeader = options.header;
    queueMicrotask(() => options.success({ statusCode: 200, data: 'response:{"status":1,"value":2}' }));
    return { abort() {} };
  } };
  const transport = new WxTransport({ wx, baseUrl: 'https://test', timeout: 1000, codec });
  const result = await transport.post('/x', { a: 1 }, 'header-code');
  assert.equal(result.value, 2);
  assert.equal(seenHeader.code, 'header-code');
  assert.equal(transport.tasks.size, 0);
});

test('empty, malformed and business failure responses are rejected', async () => {
  for (const [data, type] of [['', 'empty'], ['response:no-json', 'json'], ['response:{"status":0}', 'business']]) {
    const wx = { request(options) {
      queueMicrotask(() => options.success({ statusCode: 200, data }));
      return { abort() {} };
    } };
    const transport = new WxTransport({ wx, baseUrl: 'https://test', timeout: 1000, codec });
    await assert.rejects(transport.post('/x', {}, 'code'), (error) => error.type === type);
  }
});

test('HTTP schema failures are permanent while rate limits remain retryable', async () => {
  for (const [statusCode, retryable] of [[400, false], [429, true], [503, true]]) {
    const wx = { request(options) {
      queueMicrotask(() => options.success({ statusCode, data: 'failure' }));
      return { abort() {} };
    } };
    const transport = new WxTransport({ wx, baseUrl: 'https://test', timeout: 1000, codec });
    await assert.rejects(transport.post('/x', {}, 'code'), (error) => error.type === 'http' && error.retryable === retryable);
  }
});
