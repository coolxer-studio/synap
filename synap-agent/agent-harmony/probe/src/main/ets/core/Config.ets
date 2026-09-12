import {
  CollectionProfile,
  ResolvedAgentConfig,
  SynapAgentConfig
} from './Models';
import { SynapError } from './SynapError';

const BASELINE_PROFILE: CollectionProfile = {
  device: true,
  selfApp: true,
  runtime: true,
  network: true,
  integrity: true,
  manualBehavior: true
};

const MINIMAL_PROFILE: CollectionProfile = {
  device: true,
  selfApp: true,
  runtime: false,
  network: false,
  integrity: false,
  manualBehavior: true
};

function resolveProfile(value: SynapAgentConfig['collectionProfile']): CollectionProfile {
  if (value === undefined || value === 'baseline') {
    return { ...BASELINE_PROFILE };
  }
  if (value === 'minimal') {
    return { ...MINIMAL_PROFILE };
  }
  return {
    device: value.device === true,
    selfApp: value.selfApp === true,
    runtime: value.runtime === true,
    network: value.network === true,
    integrity: value.integrity === true,
    manualBehavior: value.manualBehavior === true
  };
}

function normalizeBaseUrl(value: string, allowInsecureHttp: boolean): string {
  const trimmed: string = String(value || '').trim().replace(/\/+$/, '');
  const https: boolean = /^https:\/\/[^\s/?#@]+(?:\/[^\s?#]*)?$/i.test(trimmed);
  const http: boolean = /^http:\/\/(localhost|127\.0\.0\.1|\[::1\])(?::\d+)?(?:\/[^\s?#]*)?$/i
    .test(trimmed);
  if (!https && !(allowInsecureHttp && http)) {
    throw new SynapError('AGENT_CONFIG_INVALID',
      'baseUrl must use HTTPS; insecure HTTP is limited to loopback when explicitly enabled');
  }
  return trimmed;
}

function normalizeAppKeyId(appId: string): string {
  const normalized: string = appId.replace(/[^A-Za-z0-9_-]/g, '_').slice(0, 64);
  return normalized.length > 0 ? normalized : 'harmony';
}

export function resolveConfig(config: SynapAgentConfig): ResolvedAgentConfig {
  if (config === undefined || config === null) {
    throw new SynapError('AGENT_CONFIG_INVALID', 'configuration is required');
  }
  const appId: string = String(config.appId || '').trim();
  if (!/^[A-Za-z0-9._:@/-]{1,128}$/.test(appId)) {
    throw new SynapError('AGENT_CONFIG_INVALID', 'appId is invalid');
  }
  if (!config.serverTrustAnchors || Object.keys(config.serverTrustAnchors).length === 0) {
    throw new SynapError('AGENT_CONFIG_INVALID', 'serverTrustAnchors is required');
  }
  const anchors: Record<string, string> = {};
  Object.keys(config.serverTrustAnchors).sort().forEach((key: string) => {
    if (!/^[\x21-\x7e]{1,128}$/.test(key) ||
      !/^[A-Za-z0-9_-]+$/.test(config.serverTrustAnchors[key])) {
      throw new SynapError('AGENT_CONFIG_INVALID', 'serverTrustAnchors contains an invalid entry');
    }
    anchors[key] = config.serverTrustAnchors[key];
  });
  const allowInsecureHttp: boolean = config.allowInsecureHttp === true;
  const requestTimeoutMs: number = config.requestTimeoutMs ?? 15000;
  const allowedClockSkewMs: number = config.allowedClockSkewMs ?? 120000;
  const queueLimit: number = config.queueLimit ?? 50;
  const batchSize: number = config.batchSize ?? 50;
  if (requestTimeoutMs < 1000 || requestTimeoutMs > 120000 ||
    allowedClockSkewMs < 0 || allowedClockSkewMs > 600000 ||
    queueLimit < 1 || queueLimit > 1000 ||
    batchSize < 1 || batchSize > 50 || batchSize > queueLimit) {
    throw new SynapError('AGENT_CONFIG_INVALID', 'numeric configuration is outside allowed limits');
  }
  const expectedCertificates: string[] = (config.expectedSigningCertificateSha256 || [])
    .map((value: string) => String(value).toLowerCase().replace(/:/g, ''));
  if (expectedCertificates.some((value: string) => !/^[0-9a-f]{64}$/.test(value))) {
    throw new SynapError('AGENT_CONFIG_INVALID',
      'expectedSigningCertificateSha256 must contain SHA-256 hex digests');
  }
  return {
    baseUrl: normalizeBaseUrl(config.baseUrl, allowInsecureHttp),
    appId,
    appKeyId: normalizeAppKeyId(appId),
    serverTrustAnchors: anchors,
    enrollmentTokenProvider: config.enrollmentTokenProvider,
    requestTimeoutMs,
    allowedClockSkewMs,
    queueLimit,
    batchSize,
    collectionProfile: resolveProfile(config.collectionProfile),
    expectedSigningCertificateSha256: Array.from(new Set<string>(expectedCertificates)).sort(),
    allowInsecureHttp,
    transport: config.transport,
    cryptoProvider: config.cryptoProvider,
    identityStore: config.identityStore,
    store: config.store
  };
}

export function sameConfig(left: ResolvedAgentConfig, right: ResolvedAgentConfig): boolean {
  return left.baseUrl === right.baseUrl && left.appId === right.appId &&
    left.requestTimeoutMs === right.requestTimeoutMs &&
    left.allowedClockSkewMs === right.allowedClockSkewMs &&
    left.queueLimit === right.queueLimit && left.batchSize === right.batchSize &&
    JSON.stringify(left.collectionProfile) === JSON.stringify(right.collectionProfile) &&
    JSON.stringify(left.serverTrustAnchors) === JSON.stringify(right.serverTrustAnchors) &&
    JSON.stringify(left.expectedSigningCertificateSha256) ===
      JSON.stringify(right.expectedSigningCertificateSha256) &&
    left.allowInsecureHttp === right.allowInsecureHttp &&
    left.enrollmentTokenProvider === right.enrollmentTokenProvider &&
    left.transport === right.transport && left.cryptoProvider === right.cryptoProvider &&
    left.identityStore === right.identityStore && left.store === right.store;
}
