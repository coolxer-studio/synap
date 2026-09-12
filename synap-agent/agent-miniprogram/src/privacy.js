'use strict';

const TOKEN_PATTERN = /((?:access[_-]?token|refresh[_-]?token|token|authorization|password|passwd|secret|session(?:id)?|code)\s*[=:]\s*)[^\s,;&]+/gi;
const PHONE_PATTERN = /\b1[3-9]\d{9}\b/g;
const EMAIL_PATTERN = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi;

function hashText(input) {
  const text = String(input || '');
  let hash = 2166136261;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `fnv1a-${(hash >>> 0).toString(16).padStart(8, '0')}`;
}

function sanitizeText(input, limit) {
  return String(input == null ? '' : input)
    .replace(TOKEN_PATTERN, '$1[masked]')
    .replace(PHONE_PATTERN, '[phone]')
    .replace(EMAIL_PATTERN, '[email]')
    .replace(/[\r\n\t]+/g, ' ')
    .slice(0, limit || 512);
}

function pathTemplate(input) {
  let value = String(input || '');
  value = value.split('#')[0].split('?')[0];
  value = value.replace(/^https?:\/\/[^/]+/i, '');
  value = value.replace(/\b[0-9a-f]{8}-[0-9a-f-]{27,}\b/gi, ':id');
  value = value.replace(/\/(\d{3,})(?=\/|$)/g, '/:id');
  return sanitizeText(value || '/', 256);
}

function routeTemplate() {
  try {
    const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
    const current = pages && pages.length ? pages[pages.length - 1] : null;
    return pathTemplate(current && (current.route || current.__route__) || '/');
  } catch (_) {
    return '/';
  }
}

function safeClone(value, maxBytes) {
  let serialized;
  try {
    serialized = JSON.stringify(value);
  } catch (_) {
    return null;
  }
  const bytes = typeof TextEncoder !== 'undefined'
    ? new TextEncoder().encode(serialized).length
    : unescape(encodeURIComponent(serialized)).length;
  if (typeof serialized !== 'string' || bytes > maxBytes) return null;
  return JSON.parse(serialized);
}

function sanitizeData(value, depth) {
  if (depth > 6) return '[masked]';
  if (typeof value === 'string') return sanitizeText(value, 2048);
  if (Array.isArray(value)) return value.slice(0, 128).map((item) => sanitizeData(item, depth + 1));
  if (value && typeof value === 'object') {
    return Object.keys(value).slice(0, 128).reduce((result, key) => {
      result[sanitizeText(key, 64)] = sanitizeData(value[key], depth + 1);
      return result;
    }, {});
  }
  return value;
}

function errorTemplate(input) {
  const firstLine = String(input == null ? '' : input).split(/\r?\n/, 1)[0];
  return sanitizeText(firstLine, 512)
    .replace(/https?:\/\/[^\s)]+/gi, (url) => pathTemplate(url))
    .replace(/\b\d{4,}\b/g, ':number');
}

module.exports = {
  hashText,
  sanitizeText,
  pathTemplate,
  routeTemplate,
  safeClone,
  sanitizeData,
  errorTemplate
};
