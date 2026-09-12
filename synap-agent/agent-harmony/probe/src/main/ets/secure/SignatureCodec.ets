import { concatBytes } from '../core/Encoding';
import { SynapError } from '../core/SynapError';

function trimInteger(value: Uint8Array): Uint8Array {
  let offset: number = 0;
  while (offset < value.length - 1 && value[offset] === 0) offset += 1;
  const body: Uint8Array = value.slice(offset);
  if ((body[0] & 0x80) !== 0) return concatBytes([new Uint8Array([0]), body]);
  return body;
}

export function p1363ToDer(signature: Uint8Array): Uint8Array {
  if (signature.length !== 64) {
    throw new SynapError('SC_HANDSHAKE_FAILED', 'P-256 signature must contain 64 bytes');
  }
  const r: Uint8Array = trimInteger(signature.slice(0, 32));
  const s: Uint8Array = trimInteger(signature.slice(32, 64));
  const sequenceLength: number = 2 + r.length + 2 + s.length;
  return concatBytes([
    new Uint8Array([0x30, sequenceLength, 0x02, r.length]), r,
    new Uint8Array([0x02, s.length]), s
  ]);
}

function readInteger(der: Uint8Array, offset: number): { value: Uint8Array, next: number } {
  if (der[offset] !== 0x02 || offset + 2 > der.length) {
    throw new SynapError('SC_IDENTITY_FAILED', 'ECDSA signature is not valid DER');
  }
  const length: number = der[offset + 1];
  const start: number = offset + 2;
  if (length < 1 || start + length > der.length) {
    throw new SynapError('SC_IDENTITY_FAILED', 'ECDSA signature is not valid DER');
  }
  let value: Uint8Array = der.slice(start, start + length);
  if (value.length === 33 && value[0] === 0) value = value.slice(1);
  if (value.length > 32) {
    throw new SynapError('SC_IDENTITY_FAILED', 'ECDSA signature integer is too large');
  }
  return { value, next: start + length };
}

export function derToP1363(signature: Uint8Array): Uint8Array {
  if (signature.length === 64) return new Uint8Array(signature);
  if (signature.length < 8 || signature[0] !== 0x30 || signature[1] !== signature.length - 2) {
    throw new SynapError('SC_IDENTITY_FAILED', 'ECDSA signature is not valid DER');
  }
  const r = readInteger(signature, 2);
  const s = readInteger(signature, r.next);
  if (s.next !== signature.length) {
    throw new SynapError('SC_IDENTITY_FAILED', 'ECDSA signature has trailing data');
  }
  const output: Uint8Array = new Uint8Array(64);
  output.set(r.value, 32 - r.value.length);
  output.set(s.value, 64 - s.value.length);
  return output;
}

