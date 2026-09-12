import { describe, expect, it } from '@ohos/hypium';
import type { common } from '@kit.AbilityKit';
import { HarmonyCollector } from '../../../main/ets/collection/HarmonyCollector';
import { CryptoFrameworkProvider } from '../../../main/ets/secure/CryptoFrameworkProvider';
import {
  base64UrlDecode,
  base64UrlEncode,
  utf8,
  utf8Text
} from '../../../main/ets/core/Encoding';

export default function deviceTests() {
  describe('agent-harmony device integration', () => {
    it('builds the 24-field Harmony common contract', 0, async () => {
      const collector = new HarmonyCollector({} as common.Context, {
        device: true, selfApp: true, runtime: true, network: true,
        integrity: true, manualBehavior: true
      }, [], new CryptoFrameworkProvider(), 'harmony-guid', 'app-id', 1785283200000);
      const commonFields = await collector.common();
      expect(commonFields.length).assertEqual(24);
      expect(String(commonFields[8])).assertEqual('harmony');
    });

    it('matches the shared AES-256-GCM request vector and rejects tampering', 0, async () => {
      const provider = new CryptoFrameworkProvider();
      const key = new Uint8Array(32);
      for (let index = 0; index < key.length; index += 1) key[index] = index;
      const iv = base64UrlDecode('oKGiowAAAAAAAAAB');
      const aad = utf8('SC1\nrequest\nP256_HKDF_SHA256_AES256_GCM\n' +
        'test-key-2026-01\ntest-session-0001\n1785283200000\n1\nrequest-0001\n' +
        'POST\n/sc/v1/message\napplication/sc-protected+json');
      const encrypted = await provider.aesGcmEncrypt(key, iv, aad, utf8('{"message":"你好🌍"}'));
      expect(base64UrlEncode(encrypted)).assertEqual(
        'y43V6x8Hp9N--JdIn9atb-inacLk7T5Q9B7Jzez-vufGdNrX0f2xKw');
      const tampered = new Uint8Array(encrypted);
      tampered[0] ^= 1;
      let rejected: boolean = false;
      try { await provider.aesGcmDecrypt(key, iv, aad, tampered); } catch (_error) { rejected = true; }
      expect(rejected).assertTrue();

      const responseKey = new Uint8Array(32);
      for (let index = 0; index < responseKey.length; index += 1) responseKey[index] = 31 - index;
      const responseAad = utf8('SC1\nresponse\nP256_HKDF_SHA256_AES256_GCM\n' +
        'test-key-2026-01\ntest-session-0001\n1785283200000\n1\nrequest-0001\n' +
        'POST\n/sc/v1/message\napplication/sc-protected+json\n200');
      const opened = await provider.aesGcmDecrypt(responseKey,
        base64UrlDecode('sLGyswAAAAAAAAAB'), responseAad,
        base64UrlDecode('2Q9-vvRfgB3ZDenLUzdtXout2_wtOCcXZu6C'));
      expect(utf8Text(opened)).assertEqual('{"ok":true}');
    });
  });
}
