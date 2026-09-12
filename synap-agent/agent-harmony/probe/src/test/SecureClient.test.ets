import { describe, expect, it } from '@ohos/hypium';
import type { common } from '@kit.AbilityKit';
import type {
  EphemeralKeyPair,
  InstallationIdentity,
  InstallationIdentityStore,
  ProtocolCryptoProvider,
  SecureRequest,
  Transport,
  TransportRequest,
  TransportResponse
} from '../main/ets/core/Models';
import { resolveConfig } from '../main/ets/core/Config';
import {
  base64UrlDecode,
  base64UrlEncode,
  nonce,
  utf8,
  utf8Text
} from '../main/ets/core/Encoding';
import { HarmonySecureClient } from '../main/ets/secure/SecureClient';
import { V1_ENVELOPE_MEDIA_TYPE } from '../main/ets/secure/V1Codec';
import { SynapAgent } from '../main/ets/SynapAgent';
import { MemoryAgentStore } from '../main/ets/storage/PreferencesAgentStore';

class MockCrypto implements ProtocolCryptoProvider {
  hmacCalls: number = 0;

  async randomBytes(length: number): Promise<Uint8Array> {
    return new Uint8Array(length).fill(9);
  }

  async sha256(_data: Uint8Array): Promise<Uint8Array> {
    return new Uint8Array(32);
  }

  async hmacSha256(_key: Uint8Array, _data: Uint8Array): Promise<Uint8Array> {
    this.hmacCalls += 1;
    return new Uint8Array(32).fill(((this.hmacCalls - 1) % 4) + 1);
  }

  async generateEphemeralP256(): Promise<EphemeralKeyPair> {
    return { publicKeySpki: new Uint8Array([7, 8, 9]), privateKey: {} };
  }

  async ecdhP256(_privateKey: Object, _peer: Uint8Array): Promise<Uint8Array> {
    return new Uint8Array(32).fill(6);
  }

  async verifyP256(_publicKey: Uint8Array, _digest: Uint8Array,
    _signature: Uint8Array): Promise<boolean> {
    return true;
  }

  async aesGcmEncrypt(_key: Uint8Array, _nonce: Uint8Array, _aad: Uint8Array,
    plaintext: Uint8Array): Promise<Uint8Array> {
    return new Uint8Array(plaintext);
  }

  async aesGcmDecrypt(_key: Uint8Array, _nonce: Uint8Array, _aad: Uint8Array,
    ciphertext: Uint8Array): Promise<Uint8Array> {
    return new Uint8Array(ciphertext);
  }
}

class MockIdentityStore implements InstallationIdentityStore {
  async loadOrCreate(_context: common.Context, _appId: string): Promise<InstallationIdentity> {
    return {
      deviceId: 'harmony-test-device',
      publicKeySpki: new Uint8Array([10, 11, 12]),
      sign: async (_data: Uint8Array): Promise<Uint8Array> => new Uint8Array(64)
    };
  }
}

interface MockEnvelope {
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

class MockTransport implements Transport {
  readonly paths: string[] = [];
  handshakeDeviceType: string = '';
  enrollmentToken: string = '';
  handshakeCount: number = 0;
  invalidateNextMessage: boolean = false;
  timeoutNextMessage: boolean = false;
  handshakeClockOffsetMs: number = 0;
  readonly uploadMessageIds: string[] = [];
  readonly uploadSequences: number[] = [];
  readonly uploadBatchIds: string[] = [];

  async post(request: TransportRequest): Promise<TransportResponse> {
    this.paths.push(request.url);
    if (request.url.endsWith('/sc/v1/handshake')) {
      this.handshakeCount += 1;
      const start = JSON.parse(request.body) as Record<string, Object>;
      this.handshakeDeviceType = String(start['deviceType']);
      this.enrollmentToken = String(start['enrollmentToken']);
      return this.json({
        v: 1,
        suite: 'P256_HKDF_SHA256_AES256_GCM',
        kid: 'test-key',
        sid: 'test-session',
        serverIdentityPublicKey: base64UrlEncode(new Uint8Array([1, 2, 3])),
        serverEphemeralPublicKey: base64UrlEncode(new Uint8Array([4, 5, 6])),
        createdAt: Date.now() + this.handshakeClockOffsetMs,
        expiresAt: Date.now() + this.handshakeClockOffsetMs + 60000,
        signature: base64UrlEncode(new Uint8Array(64))
      });
    }
    if (request.url.endsWith('/sc/v1/handshake/finish')) {
      return this.json({ active: true });
    }
    if (this.timeoutNextMessage) {
      this.timeoutNextMessage = false;
      throw new Error('mock timeout');
    }
    if (this.invalidateNextMessage) {
      this.invalidateNextMessage = false;
      return this.json({ code: 'SC_UNKNOWN_SESSION', message: 'expired', traceId: 'trace-1' }, 401);
    }
    const envelope: MockEnvelope = JSON.parse(request.body) as MockEnvelope;
    const protectedRequest = JSON.parse(utf8Text(base64UrlDecode(envelope.ct))) as Record<string, Object>;
    const logicalPath: string = String(protectedRequest['path']);
    let business: Object = { status: 1, path: logicalPath };
    if (logicalPath === '/attach/upload') {
      const body = JSON.parse(utf8Text(base64UrlDecode(String(protectedRequest['body'])))) as
        Record<string, Object>;
      const meta = body['meta'] as Record<string, Object>;
      const data = body['data'] as Array<Record<string, Object>>;
      this.uploadBatchIds.push(String(meta['batchId']));
      data.forEach((item: Record<string, Object>) => {
        this.uploadMessageIds.push(String(item['messageId']));
        this.uploadSequences.push(Number(item['sequence']));
      });
      business = {
        status: 1,
        ack: {
          batchId: String(meta['batchId']),
          acceptedMessageIds: data.map((item: Record<string, Object>) => String(item['messageId'])),
          duplicateMessageIds: [],
          rejectedItems: []
        }
      };
    }
    const protectedResponse: string = JSON.stringify({
      contentType: 'application/json',
      body: base64UrlEncode(utf8(JSON.stringify(business)))
    });
    const responseEnvelope: MockEnvelope = {
      v: 1,
      suite: envelope.suite,
      kid: envelope.kid,
      sid: envelope.sid,
      ts: Date.now(),
      seq: envelope.seq,
      rid: envelope.rid,
      m: 'POST',
      p: '/sc/v1/message',
      cty: 'application/sc-protected+json',
      st: 200,
      nonce: base64UrlEncode(nonce(new Uint8Array([4, 4, 4, 4]), envelope.seq)),
      ct: base64UrlEncode(utf8(protectedResponse))
    };
    return {
      status: 200,
      contentType: V1_ENVELOPE_MEDIA_TYPE,
      body: JSON.stringify(responseEnvelope)
    };
  }

  private json(value: Object, status: number = 200): TransportResponse {
    return { status, contentType: 'application/json', body: JSON.stringify(value) };
  }
}

export default function secureClientTests() {
  describe('HarmonySecureClient', () => {
    it('enrolls, handshakes as HARMONY and sends an encrypted logical request', 0, async () => {
      const transport = new MockTransport();
      const crypto = new MockCrypto();
      const config = resolveConfig({
        baseUrl: 'http://127.0.0.1:11099',
        appId: 'harmony-test',
        serverTrustAnchors: { 'test-key': base64UrlEncode(new Uint8Array([1, 2, 3])) },
        allowInsecureHttp: true
      });
      const client = new HarmonySecureClient({} as common.Context, config, transport, crypto,
        new MockIdentityStore());
      await client.enroll('one-time-token');
      const request: SecureRequest = {
        method: 'POST',
        logicalPath: '/cross/info',
        contentType: 'application/json',
        protectedHeaders: { code: 'harmony-code' },
        body: utf8('{}'),
        requestId: 'request-1'
      };
      const response = await client.request(request);
      const value = JSON.parse(utf8Text(response.body)) as Record<string, Object>;
      expect(transport.handshakeDeviceType).assertEqual('HARMONY');
      expect(transport.enrollmentToken).assertEqual('one-time-token');
      expect(String(value['path'])).assertEqual('/cross/info');
      expect(transport.paths.length).assertEqual(3);
    });

    it('runs idempotent agent start-stop lifecycle through mock start and upload', 0, async () => {
      const transport = new MockTransport();
      const crypto = new MockCrypto();
      const agent = await SynapAgent.initialize({} as common.Context, {
        baseUrl: 'http://127.0.0.1:11099',
        appId: 'harmony-agent-lifecycle',
        serverTrustAnchors: { 'test-key': base64UrlEncode(new Uint8Array([1, 2, 3])) },
        allowInsecureHttp: true,
        transport,
        cryptoProvider: crypto,
        identityStore: new MockIdentityStore(),
        store: new MemoryAgentStore()
      });
      await agent.start();
      await agent.start();
      expect(agent.getMetrics().status).assertEqual('running');
      expect(agent.markAction('unit-test-action')).assertTrue();
      await agent.stop({ flush: true });
      await agent.stop({ flush: true });
      expect(agent.getMetrics().status).assertEqual('stopped');
      await agent.destroy({ flush: true });
      expect(agent.getMetrics().status).assertEqual('destroyed');
      expect(transport.paths.some((path: string) => path.endsWith('/sc/v1/message'))).assertTrue();
      expect(transport.uploadMessageIds.length > 0).assertTrue();
      expect(new Set<string>(transport.uploadMessageIds).size)
        .assertEqual(transport.uploadMessageIds.length);
      expect(transport.uploadSequences.every((value: number, index: number) =>
        value >= 2 && (index === 0 || value > transport.uploadSequences[index - 1]))).assertTrue();
      expect(agent.getMetrics().batchesSent > 0).assertTrue();
    });

    it('closes an invalid session and performs a new handshake on the next request', 0, async () => {
      const transport = new MockTransport();
      const config = resolveConfig({
        baseUrl: 'http://127.0.0.1:11099', appId: 'harmony-rehandshake',
        serverTrustAnchors: { 'test-key': base64UrlEncode(new Uint8Array([1, 2, 3])) },
        allowInsecureHttp: true
      });
      const client = new HarmonySecureClient({} as common.Context, config, transport,
        new MockCrypto(), new MockIdentityStore());
      const request: SecureRequest = {
        method: 'POST', logicalPath: '/cross/info', contentType: 'application/json',
        protectedHeaders: { code: 'harmony-code' }, body: utf8('{}')
      };
      transport.invalidateNextMessage = true;
      let invalidated: boolean = false;
      try { await client.request(request); } catch (_error) { invalidated = true; }
      expect(invalidated).assertTrue();
      await client.request(request);
      expect(transport.handshakeCount).assertEqual(2);
    });

    it('rejects a handshake outside the allowed clock window', 0, async () => {
      const transport = new MockTransport();
      transport.handshakeClockOffsetMs = 300000;
      const config = resolveConfig({
        baseUrl: 'http://127.0.0.1:11099', appId: 'harmony-clock-check',
        serverTrustAnchors: { 'test-key': base64UrlEncode(new Uint8Array([1, 2, 3])) },
        allowInsecureHttp: true, allowedClockSkewMs: 120000
      });
      const client = new HarmonySecureClient({} as common.Context, config, transport,
        new MockCrypto(), new MockIdentityStore());
      let rejected: boolean = false;
      try {
        await client.request({
          method: 'POST', logicalPath: '/cross/info', contentType: 'application/json',
          protectedHeaders: {}, body: utf8('{}')
        });
      } catch (_error) { rejected = true; }
      expect(rejected).assertTrue();
    });

    it('maps transport timeouts to a stable SynapError', 0, async () => {
      const transport = new MockTransport();
      transport.timeoutNextMessage = true;
      const config = resolveConfig({
        baseUrl: 'http://127.0.0.1:11099', appId: 'harmony-timeout',
        serverTrustAnchors: { 'test-key': base64UrlEncode(new Uint8Array([1, 2, 3])) },
        allowInsecureHttp: true
      });
      const client = new HarmonySecureClient({} as common.Context, config, transport,
        new MockCrypto(), new MockIdentityStore());
      let code: string = '';
      try {
        await client.request({
          method: 'POST', logicalPath: '/cross/info', contentType: 'application/json',
          protectedHeaders: {}, body: utf8('{}')
        });
      } catch (error) { code = String((error as Record<string, Object>)['code']); }
      expect(code).assertEqual('SC_NETWORK_FAILED');
    });
  });
}
