import type { common } from '@kit.AbilityKit';
import { huks } from '@kit.UniversalKeystoreKit';
import type { InstallationIdentity, InstallationIdentityStore } from '../core/Models';
import { base64UrlEncode, utf8 } from '../core/Encoding';
import { SynapError } from '../core/SynapError';
import { CryptoFrameworkProvider } from './CryptoFrameworkProvider';
import { derToP1363 } from './SignatureCodec';

function keyOptions(purpose: number): huks.HuksOptions {
  return {
    properties: [
      { tag: huks.HuksTag.HUKS_TAG_ALGORITHM, value: huks.HuksKeyAlg.HUKS_ALG_ECC },
      { tag: huks.HuksTag.HUKS_TAG_KEY_SIZE, value: huks.HuksKeySize.HUKS_ECC_KEY_SIZE_256 },
      { tag: huks.HuksTag.HUKS_TAG_PURPOSE, value: purpose },
      { tag: huks.HuksTag.HUKS_TAG_DIGEST, value: huks.HuksKeyDigest.HUKS_DIGEST_SHA256 }
    ],
    inData: new Uint8Array(0)
  };
}

class HuksInstallationIdentity implements InstallationIdentity {
  readonly deviceId: string;
  readonly publicKeySpki: Uint8Array;
  private readonly alias: string;

  constructor(alias: string, deviceId: string, publicKeySpki: Uint8Array) {
    this.alias = alias;
    this.deviceId = deviceId;
    this.publicKeySpki = publicKeySpki;
  }

  async sign(data: Uint8Array): Promise<Uint8Array> {
    const options: huks.HuksOptions = keyOptions(huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_SIGN);
    options.inData = data;
    const session: huks.HuksSessionHandle = await huks.initSession(this.alias, options);
    const result: huks.HuksReturnResult = await huks.finishSession(session.handle, options);
    return derToP1363(result.outData || new Uint8Array(0));
  }
}

export class HuksIdentityStore implements InstallationIdentityStore {
  async loadOrCreate(_context: common.Context, appId: string): Promise<InstallationIdentity> {
    const normalizedAppId: string = appId.replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 80);
    const alias: string = `synap.installation.v1.${normalizedAppId}`;
    const purpose: number = huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_SIGN |
      huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_VERIFY;
    const options: huks.HuksOptions = keyOptions(purpose);
    try {
      const exists: boolean = await huks.isKeyItemExist(alias, options);
      if (!exists) {
        await huks.generateKeyItem(alias, options);
      }
      const exported: huks.HuksReturnResult = await huks.exportKeyItem(alias, options);
      const publicKey: Uint8Array = exported.outData || new Uint8Array(0);
      if (publicKey.length === 0) {
        throw new SynapError('SC_IDENTITY_FAILED', 'HUKS did not export the installation public key');
      }
      const digest: Uint8Array = await new CryptoFrameworkProvider().sha256(publicKey);
      const deviceId: string = `harmony-${base64UrlEncode(digest).slice(0, 43)}`;
      return new HuksInstallationIdentity(alias, deviceId, publicKey);
    } catch (error) {
      if (error instanceof SynapError) throw error;
      throw new SynapError('SC_IDENTITY_FAILED', 'HUKS installation identity failed',
        0, undefined, error as Object);
    }
  }
}
