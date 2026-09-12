import type { common } from '@kit.AbilityKit';
import type {
  InstallationIdentity,
  InstallationIdentityStore,
  ProtocolCryptoProvider,
  ResolvedAgentConfig,
  SecureRequest,
  SecureResponse,
  Transport,
  TransportResponse
} from '../core/Models';
import { SECURE_DEVICE_TYPE } from '../core/Models';
import {
  base64UrlDecode,
  base64UrlEncode,
  equalBytes,
  utf8
} from '../core/Encoding';
import { SynapError, asSynapError } from '../core/SynapError';
import { hkdfSha256 } from './CryptoFrameworkProvider';
import {
  EncodedRequest,
  V1Codec,
  V1_ENVELOPE_MEDIA_TYPE,
  V1_MESSAGE_ENDPOINT,
  V1_SUITE
} from './V1Codec';

interface HandshakeStartResponse {
  v: number;
  suite: string;
  kid: string;
  sid: string;
  serverIdentityPublicKey: string;
  serverEphemeralPublicKey: string;
  createdAt: number;
  expiresAt: number;
  signature: string;
}

interface HandshakeFinishResponse {
  active: boolean;
}

interface TransportErrorBody {
  code?: string;
  traceId?: string;
}

function parseJsonResponse<T>(response: TransportResponse, fallbackCode: string): T {
  let value: T;
  try {
    value = JSON.parse(response.body) as T;
  } catch (error) {
    throw new SynapError(fallbackCode, 'Secure transport returned invalid JSON',
      response.status, undefined, error as Object);
  }
  if (response.status < 200 || response.status >= 300) {
    const details: TransportErrorBody = value as TransportErrorBody;
    throw new SynapError(details.code || fallbackCode, 'Secure transport failed',
      response.status, details.traceId);
  }
  return value;
}

export class HarmonySecureClient {
  private readonly context: common.Context;
  private readonly config: ResolvedAgentConfig;
  private readonly transport: Transport;
  private readonly crypto: ProtocolCryptoProvider;
  private readonly identities: InstallationIdentityStore;
  private codec?: V1Codec;
  private initializePromise?: Promise<void>;
  private enrollmentToken?: string;
  private generation: number = 0;

  constructor(context: common.Context, config: ResolvedAgentConfig, transport: Transport,
    crypto: ProtocolCryptoProvider, identities: InstallationIdentityStore) {
    this.context = context;
    this.config = config;
    this.transport = transport;
    this.crypto = crypto;
    this.identities = identities;
  }

  async enroll(token: string): Promise<void> {
    if (this.codec) return;
    if (token.trim().length === 0 || token.length > 8192) {
      throw new SynapError('SC_ENROLLMENT_INVALID', 'Enrollment token is invalid');
    }
    this.enrollmentToken = token;
  }

  async initialize(): Promise<void> {
    if (this.codec) return;
    if (!this.initializePromise) {
      const expectedGeneration: number = this.generation;
      this.initializePromise = this.performInitialize(expectedGeneration).finally(() => {
        this.initializePromise = undefined;
      });
    }
    await this.initializePromise;
  }

  private async performInitialize(expectedGeneration: number): Promise<void> {
    const identity: InstallationIdentity = await this.identities.loadOrCreate(
      this.context, this.config.appId);
    const ephemeral = await this.crypto.generateEphemeralP256();
    const clientEphemeral: Uint8Array = ephemeral.publicKeySpki;
    const installation: Uint8Array = identity.publicKeySpki;
    const tokenUsed: string | undefined = this.enrollmentToken;
    let pendingMaterial: Uint8Array | undefined;
    const request = {
      v: 1,
      suite: V1_SUITE,
      appId: this.config.appId,
      deviceId: identity.deviceId,
      deviceType: SECURE_DEVICE_TYPE,
      clientEphemeralPublicKey: base64UrlEncode(clientEphemeral),
      installationPublicKey: base64UrlEncode(installation),
      enrollmentToken: tokenUsed,
      timestamp: Date.now()
    };
    try {
      const startResponse: TransportResponse = await this.transport.post({
        url: `${this.config.baseUrl}/sc/v1/handshake`,
        contentType: 'application/json',
        accept: 'application/json',
        body: JSON.stringify(request),
        timeoutMs: this.config.requestTimeoutMs
      });
      const start: HandshakeStartResponse = parseJsonResponse<HandshakeStartResponse>(
        startResponse, 'SC_HANDSHAKE_FAILED');
      const now: number = Date.now();
      if (!Number.isSafeInteger(start.createdAt) || !Number.isSafeInteger(start.expiresAt) ||
        Math.abs(now - start.createdAt) > this.config.allowedClockSkewMs ||
        start.expiresAt <= now || start.expiresAt <= start.createdAt) {
        throw new SynapError('SC_REQUEST_EXPIRED',
          'Handshake timestamp or session lifetime is invalid');
      }
      const anchor: string | undefined = this.config.serverTrustAnchors[start.kid];
      if (start.v !== 1 || start.suite !== V1_SUITE || !anchor) {
        throw new SynapError('SC_HANDSHAKE_FAILED', 'Server identity is not trusted');
      }
      const serverIdentity: Uint8Array = base64UrlDecode(start.serverIdentityPublicKey);
      if (!equalBytes(serverIdentity, base64UrlDecode(anchor))) {
        throw new SynapError('SC_HANDSHAKE_FAILED', 'Server identity pin mismatch');
      }
      const serverEphemeral: Uint8Array = base64UrlDecode(start.serverEphemeralPublicKey);
      const transcript: string = [
        'SC1-HANDSHAKE', '1', V1_SUITE, request.appId, request.deviceId,
        request.deviceType, base64UrlEncode(clientEphemeral),
        base64UrlEncode(installation), base64UrlEncode(serverIdentity),
        base64UrlEncode(serverEphemeral), start.kid, start.sid,
        String(start.createdAt), String(start.expiresAt)
      ].join('\n');
      const transcriptHash: Uint8Array = await this.crypto.sha256(utf8(transcript));
      if (!await this.crypto.verifyP256(serverIdentity, transcriptHash,
        base64UrlDecode(start.signature))) {
        throw new SynapError('SC_HANDSHAKE_FAILED', 'Server proof is invalid');
      }
      const secret: Uint8Array = await this.crypto.ecdhP256(ephemeral.privateKey, serverEphemeral);
      const material: Uint8Array = await hkdfSha256(this.crypto, secret, transcriptHash,
        utf8(`SC1/session/${V1_SUITE}/${start.sid}`), 72);
      pendingMaterial = material;
      secret.fill(0);
      const proof: Uint8Array = await identity.sign(transcriptHash);
      const finishResponse: TransportResponse = await this.transport.post({
        url: `${this.config.baseUrl}/sc/v1/handshake/finish`,
        contentType: 'application/json',
        accept: 'application/json',
        body: JSON.stringify({ kid: start.kid, sid: start.sid, proof: base64UrlEncode(proof) }),
        timeoutMs: this.config.requestTimeoutMs
      });
      const finish: HandshakeFinishResponse = parseJsonResponse<HandshakeFinishResponse>(
        finishResponse, 'SC_HANDSHAKE_FAILED');
      if (!finish.active || expectedGeneration !== this.generation) {
        throw new SynapError('SC_HANDSHAKE_FAILED', 'Handshake was not activated');
      }
      this.codec = new V1Codec({
        kid: start.kid,
        sid: start.sid,
        requestKey: material.slice(0, 32),
        responseKey: material.slice(32, 64),
        requestNoncePrefix: material.slice(64, 68),
        responseNoncePrefix: material.slice(68, 72),
        expiresAt: start.expiresAt
      }, this.crypto, this.config.allowedClockSkewMs);
      material.fill(0);
      pendingMaterial = undefined;
      if (this.enrollmentToken === tokenUsed) this.enrollmentToken = undefined;
    } catch (error) {
      pendingMaterial?.fill(0);
      this.codec = undefined;
      throw asSynapError(error as Object, 'SC_HANDSHAKE_FAILED', 'Secure handshake failed');
    }
  }

  async request(request: SecureRequest): Promise<SecureResponse> {
    await this.initialize();
    if (!this.codec) throw new SynapError('SC_UNKNOWN_SESSION', 'Secure session is unavailable');
    const requestId: string = request.requestId ||
      base64UrlEncode(await this.crypto.randomBytes(18));
    try {
      const encoded: EncodedRequest = await this.codec.encodeRequest(request, requestId);
      const response: TransportResponse = await this.transport.post({
        url: `${this.config.baseUrl}${V1_MESSAGE_ENDPOINT}`,
        contentType: V1_ENVELOPE_MEDIA_TYPE,
        accept: V1_ENVELOPE_MEDIA_TYPE,
        body: encoded.body,
        timeoutMs: this.config.requestTimeoutMs
      });
      const mediaType: string = response.contentType.split(';')[0].trim().toLowerCase();
      if (mediaType !== V1_ENVELOPE_MEDIA_TYPE) {
        let details: TransportErrorBody = {};
        try { details = JSON.parse(response.body) as TransportErrorBody; } catch (_error) {}
        throw new SynapError(details.code || 'SC_TRANSPORT_FAILED',
          'Secure transport failed', response.status, details.traceId);
      }
      return await this.codec.decodeResponse(response.body, encoded.sequence, encoded.requestId);
    } catch (error) {
      const mapped: SynapError = asSynapError(error as Object,
        'SC_NETWORK_FAILED', 'Secure request failed');
      if (mapped.code === 'SC_UNKNOWN_SESSION') this.closeSession();
      throw mapped;
    }
  }

  closeSession(): void {
    this.generation += 1;
    this.codec?.close();
    this.codec = undefined;
  }
}
