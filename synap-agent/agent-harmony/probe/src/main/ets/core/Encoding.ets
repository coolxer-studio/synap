import { util } from '@kit.ArkTS';
import { SynapError } from './SynapError';

const B64_ALPHABET: string = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

export function utf8(value: string): Uint8Array {
  return new util.TextEncoder().encodeInto(value);
}

export function utf8Text(value: Uint8Array): string {
  return new util.TextDecoder('utf-8', { fatal: true }).decodeWithStream(value, { stream: false });
}

export function concatBytes(parts: Uint8Array[]): Uint8Array {
  let length: number = 0;
  parts.forEach((part: Uint8Array) => length += part.length);
  const output: Uint8Array = new Uint8Array(length);
  let offset: number = 0;
  parts.forEach((part: Uint8Array) => {
    output.set(part, offset);
    offset += part.length;
  });
  return output;
}

export function equalBytes(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) {
    return false;
  }
  let difference: number = 0;
  for (let index: number = 0; index < left.length; index += 1) {
    difference |= left[index] ^ right[index];
  }
  return difference === 0;
}

export function base64UrlEncode(bytes: Uint8Array): string {
  let encoded: string = '';
  for (let offset: number = 0; offset < bytes.length; offset += 3) {
    const first: number = bytes[offset];
    const second: number = offset + 1 < bytes.length ? bytes[offset + 1] : 0;
    const third: number = offset + 2 < bytes.length ? bytes[offset + 2] : 0;
    const value: number = (first << 16) | (second << 8) | third;
    encoded += B64_ALPHABET[(value >>> 18) & 63];
    encoded += B64_ALPHABET[(value >>> 12) & 63];
    encoded += offset + 1 < bytes.length ? B64_ALPHABET[(value >>> 6) & 63] : '=';
    encoded += offset + 2 < bytes.length ? B64_ALPHABET[value & 63] : '=';
  }
  return encoded.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function base64UrlDecode(value: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]*$/.test(value)) {
    throw new SynapError('SC_INVALID_ENVELOPE', 'Base64URL value is invalid');
  }
  let normalized: string = value.replace(/-/g, '+').replace(/_/g, '/');
  while (normalized.length % 4 !== 0) {
    normalized += '=';
  }
  const bytes: number[] = [];
  for (let offset: number = 0; offset < normalized.length; offset += 4) {
    const a: number = B64_ALPHABET.indexOf(normalized[offset]);
    const b: number = B64_ALPHABET.indexOf(normalized[offset + 1]);
    const c: number = normalized[offset + 2] === '=' ? 0 : B64_ALPHABET.indexOf(normalized[offset + 2]);
    const d: number = normalized[offset + 3] === '=' ? 0 : B64_ALPHABET.indexOf(normalized[offset + 3]);
    if (a < 0 || b < 0 || c < 0 || d < 0) {
      throw new SynapError('SC_INVALID_ENVELOPE', 'Base64URL value is invalid');
    }
    const combined: number = (a << 18) | (b << 12) | (c << 6) | d;
    bytes.push((combined >>> 16) & 255);
    if (normalized[offset + 2] !== '=') bytes.push((combined >>> 8) & 255);
    if (normalized[offset + 3] !== '=') bytes.push(combined & 255);
  }
  const output: Uint8Array = new Uint8Array(bytes);
  if (base64UrlEncode(output) !== value) {
    throw new SynapError('SC_INVALID_ENVELOPE', 'Base64URL value is not canonical');
  }
  return output;
}

export function bytesToHex(bytes: Uint8Array): string {
  let result: string = '';
  bytes.forEach((value: number) => result += value.toString(16).padStart(2, '0'));
  return result;
}

export function nonce(prefix: Uint8Array, sequence: number): Uint8Array {
  if (prefix.length !== 4 || !Number.isSafeInteger(sequence) || sequence < 1) {
    throw new SynapError('SC_INVALID_ENVELOPE', 'nonce input is invalid');
  }
  const output: Uint8Array = new Uint8Array(12);
  output.set(prefix, 0);
  const view: DataView = new DataView(output.buffer);
  view.setUint32(4, Math.floor(sequence / 4294967296), false);
  view.setUint32(8, sequence >>> 0, false);
  return output;
}

export function formatClientTime(timestamp: number = Date.now()): string {
  const date: Date = new Date(timestamp);
  const pad = (value: number): string => value.toString().padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
