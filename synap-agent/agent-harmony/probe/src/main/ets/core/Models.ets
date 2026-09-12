import type { common } from '@kit.AbilityKit';

export const SDK_VERSION: string = '1.0.0';
export const PLATFORM: string = 'harmony';
export const SECURE_DEVICE_TYPE: string = 'HARMONY';
export const PROTOCOL_VERSION: number = 1;
export const MAX_UPLOAD_BODY_BYTES: number = 240 * 1024;
export const MAX_USER_DATA_BYTES: number = 32 * 1024;

export type AgentStatus = 'idle' | 'initialized' | 'starting' | 'running' |
  'stopping' | 'stopped' | 'destroying' | 'destroyed';

export type CollectionProfileName = 'minimal' | 'baseline';

export interface CollectionProfile {
  device: boolean;
  selfApp: boolean;
  runtime: boolean;
  network: boolean;
  integrity: boolean;
  manualBehavior: boolean;
}

export interface StopOptions {
  flush?: boolean;
}

export interface EnrollmentTokenProvider {
  getToken(): Promise<string | undefined>;
}

export interface SynapAgentConfig {
  baseUrl: string;
  appId: string;
  serverTrustAnchors: Record<string, string>;
  enrollmentTokenProvider?: EnrollmentTokenProvider;
  requestTimeoutMs?: number;
  allowedClockSkewMs?: number;
  queueLimit?: number;
  batchSize?: number;
  collectionProfile?: CollectionProfileName | CollectionProfile;
  expectedSigningCertificateSha256?: string[];
  allowInsecureHttp?: boolean;
  transport?: Transport;
  cryptoProvider?: ProtocolCryptoProvider;
  identityStore?: InstallationIdentityStore;
  store?: AgentStore;
}

export interface ResolvedAgentConfig {
  baseUrl: string;
  appId: string;
  appKeyId: string;
  serverTrustAnchors: Record<string, string>;
  enrollmentTokenProvider?: EnrollmentTokenProvider;
  requestTimeoutMs: number;
  allowedClockSkewMs: number;
  queueLimit: number;
  batchSize: number;
  collectionProfile: CollectionProfile;
  expectedSigningCertificateSha256: string[];
  allowInsecureHttp: boolean;
  transport?: Transport;
  cryptoProvider?: ProtocolCryptoProvider;
  identityStore?: InstallationIdentityStore;
  store?: AgentStore;
}

export interface AgentMessage {
  type: string;
  messageId: string;
  batchId?: string;
  sequence: number;
  observedAt: number;
  payload: Record<string, Object>;
}

export interface StartTracking {
  messageId: string;
  batchId: string;
  sessionId: string;
  attempt: number;
  createdAt: number;
  observedAt: number;
  sequence: number;
  acknowledged: boolean;
}

export interface UploadMeta {
  protocolVersion: number;
  batchId: string;
  sessionId: string;
  attempt: number;
  createdAt: number;
  appKeyId: string;
  detectorVersion: string;
}

export interface UploadBatch {
  batchId: string;
  body: string;
  messages: AgentMessage[];
}

export interface RejectedItem {
  messageId: string;
  retryable: boolean;
  reason?: string;
}

export interface UploadAck {
  batchId: string;
  acceptedMessageIds: string[];
  duplicateMessageIds: string[];
  rejectedItems: RejectedItem[];
}

export interface StartResponse {
  status: number;
  config?: Record<string, Object>;
}

export interface UploadResponse {
  status: number;
  ack?: UploadAck;
  punish?: Object[];
}

export interface QueueMetrics {
  queued: number;
  inFlight: number;
  dropped: number;
}

export interface AgentMetrics {
  status: AgentStatus;
  transportTrust: string;
  queue: QueueMetrics;
  requestFailures: number;
  parseFailures: number;
  batchesSent: number;
  retries: number;
  ackFailures: number;
  serverDuplicates: number;
  rejectedMessages: number;
  collectionFailures: number;
  lastCollectionDurationMs: number;
  lastErrorCode: string;
}

export interface AgentStore {
  initialize(context: common.Context, namespace: string): Promise<void>;
  getString(key: string, fallback?: string): Promise<string>;
  putString(key: string, value: string): Promise<void>;
  delete(key: string): Promise<void>;
}

export interface InstallationIdentity {
  deviceId: string;
  publicKeySpki: Uint8Array;
  sign(data: Uint8Array): Promise<Uint8Array>;
}

export interface InstallationIdentityStore {
  loadOrCreate(context: common.Context, appId: string): Promise<InstallationIdentity>;
}

export interface TransportRequest {
  url: string;
  contentType: string;
  accept: string;
  body: string;
  timeoutMs: number;
}

export interface TransportResponse {
  status: number;
  contentType: string;
  body: string;
}

export interface Transport {
  post(request: TransportRequest): Promise<TransportResponse>;
}

export interface EphemeralKeyPair {
  publicKeySpki: Uint8Array;
  privateKey: Object;
}

export interface ProtocolSessionMaterial {
  requestKey: Uint8Array;
  responseKey: Uint8Array;
  requestNoncePrefix: Uint8Array;
  responseNoncePrefix: Uint8Array;
}

export interface ProtocolCryptoProvider {
  randomBytes(length: number): Promise<Uint8Array>;
  sha256(data: Uint8Array): Promise<Uint8Array>;
  hmacSha256(key: Uint8Array, data: Uint8Array): Promise<Uint8Array>;
  generateEphemeralP256(): Promise<EphemeralKeyPair>;
  ecdhP256(privateKey: Object, peerPublicKeySpki: Uint8Array): Promise<Uint8Array>;
  verifyP256(publicKeySpki: Uint8Array, digest: Uint8Array,
    p1363Signature: Uint8Array): Promise<boolean>;
  aesGcmEncrypt(key: Uint8Array, nonce: Uint8Array, aad: Uint8Array,
    plaintext: Uint8Array): Promise<Uint8Array>;
  aesGcmDecrypt(key: Uint8Array, nonce: Uint8Array, aad: Uint8Array,
    ciphertextAndTag: Uint8Array): Promise<Uint8Array>;
}

export interface SecureRequest {
  method: string;
  logicalPath: string;
  contentType: string;
  protectedHeaders: Record<string, string>;
  body: Uint8Array;
  requestId?: string;
}

export interface SecureResponse {
  status: number;
  contentType: string;
  body: Uint8Array;
}

export interface CollectorSnapshot {
  common: Object[];
  facts: AgentMessage[];
}
