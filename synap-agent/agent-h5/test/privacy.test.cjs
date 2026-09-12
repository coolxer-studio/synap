const test = require('node:test');
const assert = require('node:assert/strict');

global.window = { location: { href: 'https://app.example.test/account/123456?token=secret#fragment' } };
const { sanitizeText, urlTemplate } = require('../src/lib/privacy');

test('privacy helpers remove URL secrets and common personal data', () => {
  const url = urlTemplate(window.location.href);
  assert.equal(url, 'https://app.example.test/account/:id');
  assert.doesNotMatch(url, /token|secret|fragment/);
  const text = sanitizeText('Bearer abc.def user@example.test 13800138000');
  assert.doesNotMatch(text, /abc\.def|user@example|13800138000/);
  assert.match(text, /\[masked\]/);
});
