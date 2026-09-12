import { describe, expect, it } from '@ohos/hypium';
import type { common } from '@kit.AbilityKit';
import { resolveConfig } from '../main/ets/core/Config';
import {
  base64UrlDecode,
  base64UrlEncode,
  nonce
} from '../main/ets/core/Encoding';
import { MessageQueue } from '../main/ets/core/MessageQueue';
import type {
  AgentMessage,
  UploadMeta
} from '../main/ets/core/Models';
import { splitUploadBatches, retryableMessages } from '../main/ets/core/UploadBatcher';
import { validateUserData } from '../main/ets/core/Privacy';
import { MemoryAgentStore } from '../main/ets/storage/PreferencesAgentStore';
import { bytesToHex } from '../main/ets/core/Encoding';
import { derToP1363, p1363ToDer } from '../main/ets/secure/SignatureCodec';
import secureClientTests from './SecureClient.test';

function message(index: number, value: string = 'ok'): AgentMessage {
  return {
    type: 'HarmonyDevice',
    messageId: `message-${index}`,
    sequence: index + 2,
    observedAt: 1785283200000 + index,
    payload: { value }
  };
}

function meta(index: number): UploadMeta {
  return {
    protocolVersion: 1,
    batchId: `batch-${index}`,
    sessionId: 'session-1',
    attempt: 1,
    createdAt: 1785283200000,
    appKeyId: 'app',
    detectorVersion: '1.0.0'
  };
}

export default function agentHarmonyTests() {
  describe('agent-harmony core', () => {
    it('validates HTTPS and explicit loopback HTTP', 0, () => {
      const secure = resolveConfig({
        baseUrl: 'https://risk.example.com/', appId: 'harmony-app',
        serverTrustAnchors: { key: 'AQID' }
      });
      expect(secure.baseUrl).assertEqual('https://risk.example.com');
      const local = resolveConfig({
        baseUrl: 'http://127.0.0.1:11099', appId: 'harmony-app',
        serverTrustAnchors: { key: 'AQID' }, allowInsecureHttp: true
      });
      expect(local.queueLimit).assertEqual(50);
      expect(local.batchSize).assertEqual(50);
    });

    it('round trips Base64URL and builds canonical nonce', 0, () => {
      const bytes = new Uint8Array([0, 1, 2, 253, 254, 255]);
      expect(Array.from(base64UrlDecode(base64UrlEncode(bytes))).join(','))
        .assertEqual(Array.from(bytes).join(','));
      expect(base64UrlEncode(nonce(new Uint8Array([0xa0, 0xa1, 0xa2, 0xa3]), 1)))
        .assertEqual('oKGiowAAAAAAAAAB');
    });

    it('persists, restores and bounds the queue', 0, async () => {
      const store = new MemoryAgentStore();
      await store.initialize({} as common.Context, 'test');
      const first = new MessageQueue(store, 2);
      expect(first.enqueue(message(0))).assertTrue();
      expect(first.enqueue(message(1))).assertTrue();
      expect(first.enqueue(message(2))).assertFalse();
      await first.persist();
      const restored = new MessageQueue(store, 2);
      await restored.restore();
      expect(restored.metrics().queued).assertEqual(2);
      expect(restored.highestSequence()).assertEqual(3);
      const inFlight = restored.takeBatch(2);
      expect(restored.metrics().inFlight).assertEqual(2);
      restored.restoreBatch(inFlight);
      expect(restored.metrics().queued).assertEqual(2);
      expect(restored.metrics().inFlight).assertEqual(0);
      await restored.persist();
      const afterInterruptedRestore = new MessageQueue(store, 2);
      await afterInterruptedRestore.restore();
      expect(afterInterruptedRestore.metrics().queued).assertEqual(2);
    });

    it('splits UTF-8 batches and classifies ACKs', 0, () => {
      const result = splitUploadBatches(new Array<Object>(24).fill(''),
        [message(0, '你'.repeat(30)), message(1, '好'.repeat(30))], 1, 4096, meta);
      expect(result.batches.length).assertEqual(2);
      const retry = retryableMessages(result.batches[0], 'batch-0', [], [], [
        { messageId: 'message-0', retryable: true }
      ]);
      expect(retry.length).assertEqual(1);
      const retried = splitUploadBatches(new Array<Object>(24).fill(''), retry, 1, 4096, meta);
      expect(retried.batches[0].batchId).assertEqual('batch-0');
    });

    it('rejects sensitive or oversized user data', 0, () => {
      expect(validateUserData('user', { tier: 'gold' })).assertTrue();
      expect(validateUserData('user', { password: 'secret' })).assertFalse();
      expect(validateUserData('user', { '手机': '13800000000' })).assertFalse();
      expect(validateUserData('user', { value: 'x'.repeat(33 * 1024) })).assertFalse();
    });

    it('rejects malformed expected signing certificate digests', 0, () => {
      let rejected: boolean = false;
      try {
        resolveConfig({
          baseUrl: 'https://risk.example.com', appId: 'harmony-app',
          serverTrustAnchors: { key: 'AQID' }, expectedSigningCertificateSha256: ['not-a-digest']
        });
      } catch (_error) { rejected = true; }
      expect(rejected).assertTrue();
    });

    it('converts ECDSA P1363 and DER without loss', 0, () => {
      const signature = new Uint8Array(64);
      signature[0] = 0x80;
      signature[31] = 1;
      signature[32] = 0x7f;
      signature[63] = 2;
      expect(bytesToHex(derToP1363(p1363ToDer(signature)))).assertEqual(bytesToHex(signature));
    });

  });
  secureClientTests();
}
