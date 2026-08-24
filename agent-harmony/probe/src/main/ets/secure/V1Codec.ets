import type {
  ProtocolCryptoProvider,
  SecureRequest,
  SecureResponse
} from '../core/Models';
import {
  base64UrlDecode,
  base64UrlEncode,
  equalBytes,
  nonce,
  utf8,
  utf8Text
} from '../core/Encoding';
import { SynapError } from '../core/SynapError';

export const V1_SUITE: string = 'P256_HKDF_SHA256_AES256_GCM';
export const V1_ENVELOPE_MEDIA_TYPE: string = 'application/sc-envelope+json';
export const V1_PROTECTED_MEDIA_TYPE: string = 'application/sc-protected+json';
export const V1_MESSAGE_ENDPOINT: string = '/sc/v1/message';

export interface V1Session {
  kid: string;
  sid: string;
  requestKey: Uint8Array;
  responseKey: Uint8Array;
  requestNoncePrefix: Uint8Array;
  responseNoncePrefix: Uint8Array;
  expiresAt: number;
}

interface V1Envelope {
  v: number;
  suite: string;
  kid: string;
  sid: string;
  ts: number;
  seq: number;
  rid: string;
  m: string;
  p: string;
  cty: string;
  st: number;
  nonce: string;
  ct: string;
}

interface ProtectedResponse {
  contentType: string;
  body: string;
}

export interface EncodedRequest {
  body: string;
  sequence: number;
  requestId: string;
}

function requireIdentifier(value: string, name: string): void {
  if (!/^[\x21-\x7e]{1,128}$/.test(value)) {
    throw new SynapError('SC_INVALID_ENVELOPE', `${name} is invalid`);
  }
}

function normalizeMethod(value: string): string {
  const method: string = value.toUpperCase();
  if (!/^[A-Z]{3,16}$/.test(method)) {
    throw new SynapError('SC_ROUTE_MISMATCH', 'logical method is invalid');
  }
  return method;
}

function uppercasePercentHex(value: string): string {
  let result: string = '';
  for (let index: number = 0; index < value.length; index += 1) {
    if (value[index] !== '%') {
      result += value[index];
      continue;
    }
    if (index + 2 >= value.length || !/^[0-9A-Fa-f]{2}$/.test(value.slice(index + 1, index + 3))) {
      throw new SynapError('SC_ROUTE_MISMATCH', 'logical path has invalid percent encoding');
    }
    result += `%${value.slice(index + 1, index + 3).toUpperCase()}`;
    index += 2;
  }
  return result;
}

function normalizePath(value: string): string {
  if (!value.startsWith('/') || /[\r\n #]/.test(value) || value.includes('://')) {
    throw new SynapError('SC_ROUTE_MISMATCH', 'logical path is invalid');
  }
  const split: string[] = value.split('?');
  if (split.length > 2) throw new SynapError('SC_ROUTE_MISMATCH', 'logical path is invalid');
  const path: string = uppercasePercentHex(split[0]);
  if (split.length === 1 || split[1].length === 0) return path;
  const pairs: string[] = split[1].split('&').filter((part: string) => part.length > 0)
    .map(uppercasePercentHex).sort();
  return pairs.length > 0 ? `${path}?${pairs.join('&')}` : path;
}

function normalizeContentType(value: string): string {
  const normalized: string = (value || 'application/octet-stream').split(';')[0].trim().toLowerCase();
  if (!/^[a-z0-9!#$&^_.+-]+\/[a-z0-9!#$&^_.+-]+$/.test(normalized)) {
    throw new SynapError('SC_ROUTE_MISMATCH', 'content type is invalid');
  }
  return normalized;
}

function aad(direction: string, envelope: V1Envelope): Uint8Array {
  const lines: string[] = [
    'SC1', direction, envelope.suite, envelope.kid, envelope.sid,
    String(envelope.ts), String(envelope.seq), envelope.rid,
    envelope.m, envelope.p, envelope.cty
  ];
  if (direction === 'response') lines.push(String(envelope.st));
  return utf8(lines.join('\n'));
}

function assertEnvelopeShape(envelope: V1Envelope): void {
  const expected: string = 'ct|cty|kid|m|nonce|p|rid|seq|sid|st|suite|ts|v';
  if (Object.keys(envelope).sort().join('|') !== expected) {
    throw new SynapError('SC_INVALID_ENVELOPE', 'Envelope fields are invalid');
  }
}

export class V1Codec {
  private readonly session: V1Session;
  private readonly crypto: ProtocolCryptoProvider;
  private readonly allowedClockSkewMs: number;
  private nextSequence: number = 1;

  constructor(session: V1Session, crypto: ProtocolCryptoProvider, allowedClockSkewMs: number) {
    this.session = session;
    this.crypto = crypto;
    this.allowedClockSkewMs = allowedClockSkewMs;
  }

  close(): void {
    this.session.requestKey.fill(0);
    this.session.responseKey.fill(0);
    this.session.requestNoncePrefix.fill(0);
    this.session.responseNoncePrefix.fill(0);
    this.nextSequence = 0;
  }

  async encodeRequest(request: SecureRequest, requestId: string): Promise<EncodedRequest> {
    if (!Number.isSafeInteger(this.nextSequence) || this.nextSequence < 1) {
      throw new SynapError('SC_SEQUENCE_EXHAUSTED', 'Session sequence is exhausted');
    }
    requireIdentifier(requestId, 'requestId');
    const sequence: number = this.nextSequence++;
    const timestamp: number = Date.now();
    if (timestamp >= this.session.expiresAt) {
      throw new SynapError('SC_UNKNOWN_SESSION', 'Session has expired');
    }
    const protectedHeaders: Record<string, string> = {};
    Object.keys(request.protectedHeaders).forEach((name: string) => {
      const normalized: string = name.toLowerCase();
      const value: string = request.protectedHeaders[name];
      if (!/^[a-z0-9-]{1,64}$/.test(normalized) || /[\r\n]/.test(value) || value.length > 8192) {
        throw new SynapError('SC_ROUTE_MISMATCH', 'protected header is invalid');
      }
      protectedHeaders[normalized] = value;
    });
    const payload: string = JSON.stringify({
      method: normalizeMethod(request.method),
      path: normalizePath(request.logicalPath),
      contentType: normalizeContentType(request.contentType),
      headers: protectedHeaders,
      body: base64UrlEncode(request.body)
    });
    const envelope: V1Envelope = {
      v: 1,
      suite: V1_SUITE,
      kid: this.session.kid,
      sid: this.session.sid,
      ts: timestamp,
      seq: sequence,
      rid: requestId,
      m: 'POST',
      p: V1_MESSAGE_ENDPOINT,
      cty: V1_PROTECTED_MEDIA_TYPE,
      st: 0,
      nonce: '',
      ct: ''
    };
    const requestNonce: Uint8Array = nonce(this.session.requestNoncePrefix, sequence);
    envelope.nonce = base64UrlEncode(requestNonce);
    envelope.ct = base64UrlEncode(await this.crypto.aesGcmEncrypt(
      this.session.requestKey, requestNonce, aad('request', envelope), utf8(payload)));
    return { body: JSON.stringify(envelope), sequence, requestId };
  }

  async decodeResponse(encoded: string, expectedSequence: number,
    expectedRequestId: string): Promise<SecureResponse> {
    let envelope: V1Envelope;
    try {
      envelope = JSON.parse(encoded) as V1Envelope;
    } catch (error) {
      throw new SynapError('SC_INVALID_ENVELOPE', 'Response is not valid JSON',
        0, undefined, error as Object);
    }
    assertEnvelopeShape(envelope);
    requireIdentifier(envelope.rid, 'rid');
    if (envelope.v !== 1 || envelope.suite !== V1_SUITE ||
      envelope.kid !== this.session.kid || envelope.sid !== this.session.sid) {
      throw new SynapError('SC_UNKNOWN_SESSION', 'Response session does not match');
    }
    if (envelope.seq !== expectedSequence || envelope.rid !== expectedRequestId ||
      envelope.m !== 'POST' || envelope.p !== V1_MESSAGE_ENDPOINT ||
      envelope.cty !== V1_PROTECTED_MEDIA_TYPE || envelope.st < 100 || envelope.st > 599) {
      throw new SynapError('SC_ROUTE_MISMATCH', 'Response does not match request');
    }
    if (Math.abs(Date.now() - envelope.ts) > this.allowedClockSkewMs) {
      throw new SynapError('SC_REQUEST_EXPIRED', 'Response timestamp is outside allowed skew');
    }
    const expectedNonce: Uint8Array = nonce(this.session.responseNoncePrefix, envelope.seq);
    const responseNonce: Uint8Array = base64UrlDecode(envelope.nonce);
    if (!equalBytes(expectedNonce, responseNonce)) {
      throw new SynapError('SC_INVALID_ENVELOPE', 'Response nonce is invalid');
    }
    let opened: Uint8Array;
    try {
      opened = await this.crypto.aesGcmDecrypt(this.session.responseKey, responseNonce,
        aad('response', envelope), base64UrlDecode(envelope.ct));
    } catch (error) {
      throw new SynapError('SC_AUTHENTICATION_FAILED', 'Response authentication failed',
        0, undefined, error as Object);
    }
    let protectedResponse: ProtectedResponse;
    try {
      protectedResponse = JSON.parse(utf8Text(opened)) as ProtectedResponse;
    } catch (error) {
      throw new SynapError('SC_INVALID_ENVELOPE', 'Protected response is invalid',
        0, undefined, error as Object);
    }
    if (Object.keys(protectedResponse).sort().join('|') !== 'body|contentType') {
      throw new SynapError('SC_INVALID_ENVELOPE', 'Protected response fields are invalid');
    }
    return {
      status: envelope.st,
      contentType: normalizeContentType(protectedResponse.contentType),
      body: base64UrlDecode(protectedResponse.body)
    };
  }
}
