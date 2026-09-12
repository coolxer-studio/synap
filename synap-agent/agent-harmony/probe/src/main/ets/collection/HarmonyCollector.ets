import type { common } from '@kit.AbilityKit';
import { bundleManager } from '@kit.AbilityKit';
import { deviceInfo } from '@kit.BasicServicesKit';
import { connection } from '@kit.NetworkKit';
import type {
  AgentMessage,
  CollectionProfile,
  ProtocolCryptoProvider
} from '../core/Models';
import {
  base64UrlDecode,
  bytesToHex,
  formatClientTime,
  utf8
} from '../core/Encoding';
import { PLATFORM, SDK_VERSION } from '../core/Models';

interface AppSnapshot {
  name: string;
  bundleName: string;
  versionName: string;
  versionCode: number;
  debuggable: boolean;
  certificateSha256: string[];
}

interface NetworkSnapshot {
  type: string;
  metered: string;
  vpn: boolean;
  localAddress: string;
}

export type MessageFactory = (type: string, payload: Record<string, Object>) => AgentMessage;

function certificateBytes(value: string): Uint8Array {
  const normalized: string = value
    .replace(/-----BEGIN CERTIFICATE-----/g, '')
    .replace(/-----END CERTIFICATE-----/g, '')
    .replace(/\s/g, '');
  if (/^[A-Za-z0-9+/]+={0,2}$/.test(normalized)) {
    try {
      return base64UrlDecode(normalized.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''));
    } catch (_error) {}
  }
  return utf8(value);
}

function bearerName(type: connection.NetBearType): string {
  switch (type) {
    case connection.NetBearType.BEARER_WIFI: return 'Wi-Fi';
    case connection.NetBearType.BEARER_CELLULAR: return 'Cellular';
    case connection.NetBearType.BEARER_ETHERNET: return 'Ethernet';
    case connection.NetBearType.BEARER_VPN: return 'VPN';
    default: return 'unknown';
  }
}

export class HarmonyCollector {
  private readonly context: common.Context;
  private readonly profile: CollectionProfile;
  private readonly expectedCertificates: string[];
  private readonly crypto: ProtocolCryptoProvider;
  private readonly deviceId: string;
  private readonly appId: string;
  private readonly startId: number;
  private userId: string = '';
  private app?: AppSnapshot;

  constructor(context: common.Context, profile: CollectionProfile, expectedCertificates: string[],
    crypto: ProtocolCryptoProvider, deviceId: string, appId: string, startId: number) {
    this.context = context;
    this.profile = profile;
    this.expectedCertificates = expectedCertificates;
    this.crypto = crypto;
    this.deviceId = deviceId;
    this.appId = appId;
    this.startId = startId;
  }

  setUserId(userId: string): void {
    this.userId = userId;
  }

  private async appSnapshot(): Promise<AppSnapshot> {
    if (this.app) return this.app;
    try {
      const flags: number = bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION |
        bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_SIGNATURE_INFO;
      const info: bundleManager.BundleInfo = await bundleManager.getBundleInfoForSelf(flags);
      const certificates: string[] = info.signatureInfo?.signingCertificateHistory || [];
      const certificateSha256: string[] = [];
      for (const certificate of certificates) {
        certificateSha256.push(bytesToHex(await this.crypto.sha256(certificateBytes(certificate))));
      }
      let label: string = info.name;
      const labelId: number = info.appInfo?.labelResource?.id || 0;
      if (labelId > 0) {
        try { label = await this.context.resourceManager.getStringValue(labelId); } catch (_error) {}
      }
      this.app = {
        name: label,
        bundleName: info.name,
        versionName: info.versionName,
        versionCode: info.versionCode,
        debuggable: info.appInfo?.debug === true,
        certificateSha256
      };
    } catch (_error) {
      this.app = {
        name: '', bundleName: '', versionName: '', versionCode: 0,
        debuggable: false, certificateSha256: []
      };
    }
    return this.app;
  }

  private async networkSnapshot(): Promise<NetworkSnapshot> {
    const snapshot: NetworkSnapshot = {
      type: 'unknown', metered: 'unknown', vpn: false, localAddress: ''
    };
    try {
      const handle: connection.NetHandle = await connection.getDefaultNet();
      const capabilities: connection.NetCapabilities = await connection.getNetCapabilities(handle);
      const bearers: connection.NetBearType[] = capabilities.bearerTypes || [];
      snapshot.type = bearers.length > 0 ? bearerName(bearers[0]) : 'unknown';
      snapshot.vpn = bearers.includes(connection.NetBearType.BEARER_VPN);
      const properties: connection.ConnectionProperties = await connection.getConnectionProperties(handle);
      if (properties.linkAddresses && properties.linkAddresses.length > 0) {
        snapshot.localAddress = properties.linkAddresses[0].address.address;
      }
    } catch (_error) {}
    return snapshot;
  }

  async common(): Promise<Object[]> {
    const app: AppSnapshot = await this.appSnapshot();
    const network: NetworkSnapshot = await this.networkSnapshot();
    return [
      this.userId,
      this.deviceId,
      this.startId,
      SDK_VERSION,
      this.appId,
      app.name,
      app.bundleName,
      app.versionName,
      PLATFORM,
      deviceInfo.manufacture || '',
      deviceInfo.productModel || '',
      'HarmonyOS',
      deviceInfo.osFullName || '',
      network.type,
      network.localAddress,
      '',
      '', '', '', '', '', '', '',
      formatClientTime()
    ];
  }

  async collect(factory: MessageFactory): Promise<AgentMessage[]> {
    const facts: AgentMessage[] = [];
    const app: AppSnapshot = await this.appSnapshot();
    if (this.profile.device) {
      facts.push(factory('HarmonyDevice', {
        deviceId: this.deviceId,
        brand: deviceInfo.brand || '',
        manufacturer: deviceInfo.manufacture || '',
        model: deviceInfo.productModel || '',
        productSeries: deviceInfo.productSeries || '',
        os: 'HarmonyOS',
        osFullName: deviceInfo.osFullName || '',
        apiVersion: deviceInfo.sdkApiVersion,
        locale: Intl.DateTimeFormat().resolvedOptions().locale,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
      }));
    }
    if (this.profile.selfApp) {
      facts.push(factory('HarmonySelfApp', {
        appName: app.name,
        bundleName: app.bundleName,
        versionName: app.versionName,
        versionCode: app.versionCode,
        debuggable: app.debuggable,
        certificateSha256: app.certificateSha256
      }));
    }
    if (this.profile.runtime) {
      facts.push(factory('HarmonyRuntime', {
        processScope: 'application',
        backgroundCollection: false,
        debuggerState: app.debuggable ? 'debug-build' : 'not-reported',
        emulatorState: 'unknown',
        privilegeState: 'sandboxed-public-api-only'
      }));
    }
    if (this.profile.network) {
      const network: NetworkSnapshot = await this.networkSnapshot();
      facts.push(factory('HarmonyNetwork', {
        bearer: network.type,
        metered: network.metered,
        vpn: network.vpn,
        localAddress: network.localAddress,
        publicAddress: ''
      }));
    }
    if (this.profile.integrity) {
      const expected: Set<string> = new Set<string>(this.expectedCertificates);
      const matched: boolean = expected.size === 0 ||
        app.certificateSha256.some((value: string) => expected.has(value));
      facts.push(factory('HarmonyIntegrity', {
        certificateSha256: app.certificateSha256,
        expectedCertificateConfigured: expected.size > 0,
        certificateMatched: matched,
        debuggable: app.debuggable,
        status: matched && !app.debuggable ? 'ok' : 'attention'
      }));
    }
    return facts;
  }
}
