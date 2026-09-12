import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import type {
  EphemeralKeyPair,
  ProtocolCryptoProvider
} from '../core/Models';
import { concatBytes } from '../core/Encoding';
import { SynapError } from '../core/SynapError';
import { p1363ToDer } from './SignatureCodec';

interface HeldPrivateKey {
  key: cryptoFramework.PriKey;
}

function blob(data: Uint8Array): cryptoFramework.DataBlob {
  return { data };
}

async function rawAesKey(data: Uint8Array): Promise<cryptoFramework.SymKey> {
  const generator: cryptoFramework.SymKeyGenerator = cryptoFramework.createSymKeyGenerator('AES256');
  return generator.convertKey(blob(data));
}

export class CryptoFrameworkProvider implements ProtocolCryptoProvider {
  async randomBytes(length: number): Promise<Uint8Array> {
    try {
      const random: cryptoFramework.Random = cryptoFramework.createRandom();
      return (await random.generateRandom(length)).data;
    } catch (error) {
      throw new SynapError('SC_CRYPTO_UNAVAILABLE', 'Secure random generation failed',
        0, undefined, error as Object);
    }
  }

  async sha256(data: Uint8Array): Promise<Uint8Array> {
    const digest: cryptoFramework.Md = cryptoFramework.createMd('SHA256');
    await digest.update(blob(data));
    return (await digest.digest()).data;
  }

  async hmacSha256(key: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
    const keyGenerator: cryptoFramework.SymKeyGenerator =
      cryptoFramework.createSymKeyGenerator('HMAC');
    const symKey: cryptoFramework.SymKey = await keyGenerator.convertKey(blob(key));
    const mac: cryptoFramework.Mac = cryptoFramework.createMac('SHA256');
    await mac.init(symKey);
    await mac.update(blob(data));
    return (await mac.doFinal()).data;
  }

  async generateEphemeralP256(): Promise<EphemeralKeyPair> {
    const generator: cryptoFramework.AsyKeyGenerator = cryptoFramework.createAsyKeyGenerator('ECC256');
    const pair: cryptoFramework.KeyPair = await generator.generateKeyPair();
    return {
      publicKeySpki: (await pair.pubKey.getEncoded()).data,
      privateKey: { key: pair.priKey } as HeldPrivateKey
    };
  }

  async ecdhP256(privateKey: Object, peerPublicKeySpki: Uint8Array): Promise<Uint8Array> {
    const generator: cryptoFramework.AsyKeyGenerator = cryptoFramework.createAsyKeyGenerator('ECC256');
    const pair: cryptoFramework.KeyPair = await generator.convertKey(blob(peerPublicKeySpki), null);
    const agreement: cryptoFramework.KeyAgreement = cryptoFramework.createKeyAgreement('ECC256');
    const held: HeldPrivateKey = privateKey as HeldPrivateKey;
    return (await agreement.generateSecret(held.key, pair.pubKey)).data;
  }

  async verifyP256(publicKeySpki: Uint8Array, digest: Uint8Array,
    p1363Signature: Uint8Array): Promise<boolean> {
    const generator: cryptoFramework.AsyKeyGenerator = cryptoFramework.createAsyKeyGenerator('ECC256');
    const pair: cryptoFramework.KeyPair = await generator.convertKey(blob(publicKeySpki), null);
    const verifier: cryptoFramework.Verify = cryptoFramework.createVerify('ECC256|SHA256');
    await verifier.init(pair.pubKey);
    await verifier.update(blob(digest));
    return verifier.verify(blob(p1363ToDer(p1363Signature)));
  }

  async aesGcmEncrypt(key: Uint8Array, nonce: Uint8Array, aad: Uint8Array,
    plaintext: Uint8Array): Promise<Uint8Array> {
    const cipher: cryptoFramework.Cipher = cryptoFramework.createCipher('AES256|GCM|NoPadding');
    const params: cryptoFramework.GcmParamsSpec = {
      algName: 'GcmParamsSpec',
      iv: blob(nonce),
      aad: blob(aad),
      authTag: blob(new Uint8Array(16))
    };
    await cipher.init(cryptoFramework.CryptoMode.ENCRYPT_MODE, await rawAesKey(key), params);
    const encrypted: cryptoFramework.DataBlob = await cipher.update(blob(plaintext));
    const tag: cryptoFramework.DataBlob | null = await cipher.doFinal(null);
    if (tag === null || tag.data.length !== 16) {
      throw new SynapError('SC_CRYPTO_FAILED', 'AES-GCM did not return a valid authentication tag');
    }
    return concatBytes([encrypted.data, tag.data]);
  }

  async aesGcmDecrypt(key: Uint8Array, nonce: Uint8Array, aad: Uint8Array,
    ciphertextAndTag: Uint8Array): Promise<Uint8Array> {
    if (ciphertextAndTag.length < 16) {
      throw new SynapError('SC_INVALID_ENVELOPE', 'AES-GCM ciphertext is too short');
    }
    const ciphertext: Uint8Array = ciphertextAndTag.slice(0, -16);
    const tag: Uint8Array = ciphertextAndTag.slice(-16);
    const cipher: cryptoFramework.Cipher = cryptoFramework.createCipher('AES256|GCM|NoPadding');
    const params: cryptoFramework.GcmParamsSpec = {
      algName: 'GcmParamsSpec',
      iv: blob(nonce),
      aad: blob(aad),
      authTag: blob(tag)
    };
    await cipher.init(cryptoFramework.CryptoMode.DECRYPT_MODE, await rawAesKey(key), params);
    const plaintext: cryptoFramework.DataBlob = await cipher.update(blob(ciphertext));
    // doFinal verifies authTag; authentication failures must propagate to the caller.
    await cipher.doFinal(null);
    return plaintext.data;
  }
}

export async function hkdfSha256(provider: ProtocolCryptoProvider, secret: Uint8Array,
  salt: Uint8Array, info: Uint8Array, length: number): Promise<Uint8Array> {
  const pseudoRandomKey: Uint8Array = await provider.hmacSha256(salt, secret);
  const blocks: Uint8Array[] = [];
  let previous: Uint8Array = new Uint8Array(0);
  let produced: number = 0;
  try {
    for (let counter: number = 1; produced < length; counter += 1) {
      if (counter > 255) {
        throw new SynapError('SC_CRYPTO_FAILED', 'HKDF output is too long');
      }
      previous = await provider.hmacSha256(pseudoRandomKey,
        concatBytes([previous, info, new Uint8Array([counter])]));
      blocks.push(previous);
      produced += previous.length;
    }
    return concatBytes(blocks).slice(0, length);
  } finally {
    pseudoRandomKey.fill(0);
    blocks.forEach((block: Uint8Array) => block.fill(0));
  }
}
