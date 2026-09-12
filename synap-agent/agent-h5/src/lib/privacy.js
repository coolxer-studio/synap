const SECRET_PATTERN = /(bearer\s+)[a-z0-9._~+/=-]+|([?&#](?:token|access_token|auth|password|code)=)[^&#\s]+|[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}|\b1[3-9]\d{9}\b/gi;

export function sanitizeText(value, maxLength = 256) {
  return String(value == null ? '' : value)
    .replace(SECRET_PATTERN, (match, bearer, query) => bearer ? `${bearer}[masked]` : query ? `${query}[masked]` : '[masked]')
    .replace(/[\r\n\t]+/g, ' ')
    .slice(0, maxLength);
}

export function urlTemplate(value) {
  try {
    const url = new URL(String(value || ''), window.location.href);
    const path = url.pathname.split('/').map((segment) => {
      if (/^\d{4,}$/.test(segment) || /^[0-9a-f]{8}-[0-9a-f-]{20,}$/i.test(segment)) return ':id';
      return sanitizeText(segment, 80);
    }).join('/');
    return `${url.origin}${path}`.slice(0, 512);
  } catch (_) { return 'invalid-url'; }
}

export function textHash(value) {
  const text = String(value || '');
  let hash = 2166136261;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `fnv1a-${(hash >>> 0).toString(16).padStart(8, '0')}`;
}

export function safeElementId(value) {
  const id = String(value || '');
  return /^[a-z][a-z0-9_-]{0,63}$/i.test(id) ? id : '';
}
