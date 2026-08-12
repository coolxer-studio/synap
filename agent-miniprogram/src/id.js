'use strict';

function randomBytes(length, wxApi) {
  const bytes = new Uint8Array(length);
  if (wxApi && typeof wxApi.getRandomValues === 'function') {
    try {
      wxApi.getRandomValues(bytes);
      return bytes;
    } catch (_) { /* use a non-cryptographic fallback for identifiers */ }
  }
  for (let index = 0; index < length; index += 1) bytes[index] = Math.floor(Math.random() * 256);
  return bytes;
}

function uuid(wxApi) {
  const bytes = randomBytes(16, wxApi);
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

module.exports = { uuid };
