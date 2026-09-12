import type { common } from '@kit.AbilityKit';
import {
  AgentMessage,
  AgentMetrics,
  AgentStatus,
  AgentStore,
  InstallationIdentity,
  InstallationIdentityStore,
  MAX_UPLOAD_BODY_BYTES,
  PROTOCOL_VERSION,
  ProtocolCryptoProvider,
  ResolvedAgentConfig,
  SDK_VERSION,
  SecureResponse,
  StartResponse,
  StartTracking,
  StopOptions,
  SynapAgentConfig,
  Transport,
  UploadBatch,
  UploadMeta,
  UploadResponse
} from './core/Models';
import { resolveConfig, sameConfig } from './core/Config';
import {
  base64UrlEncode,
  utf8,
  utf8Text
} from './core/Encoding';
import { MessageQueue } from './core/MessageQueue';
import {
  BatchResult,
  retryableMessages,
  splitUploadBatches
} from './core/UploadBatcher';
import { validateAction, validateUserData } from './core/Privacy';
import { SynapError, asSynapError } from './core/SynapError';
import { HarmonyCollector } from './collection/HarmonyCollector';
import { PreferencesAgentStore } from './storage/PreferencesAgentStore';
import { CryptoFrameworkProvider } from './secure/CryptoFrameworkProvider';
import { HuksIdentityStore } from './secure/HuksIdentityStore';
import { HarmonySecureClient } from './secure/SecureClient';
import { HttpTransport } from './transport/HttpTransport';

const START_STORAGE_KEY: string = 'start_tracking_v1';
const START_ID_STORAGE_KEY: string = 'runtime_start_id_v1';
const NEXT_SEQUENCE_STORAGE_KEY: string = 'next_message_sequence_v1';

interface QueryMeta {
  protocolVersion: number;
  appKeyId: string;
  detectorVersion: string;
}

interface StartMeta extends QueryMeta {
  messageId: string;
  batchId: string;
  sessionId: string;
  attempt: number;
  createdAt: number;
  observedAt: number;
  sequence: number;
}

function responseJson<T>(response: SecureResponse): T {
  try {
    return JSON.parse(utf8Text(response.body)) as T;
  } catch (error) {
    throw new SynapError('AGENT_RESPONSE_INVALID', 'Agent response is invalid JSON',
      response.status, undefined, error as Object);
  }
}

function configIntervals(config?: Record<string, Object>): { check: number, send: number } {
  const startConfig: Object[] = (config?.['startConfig'] as Object[]) || [];
  const checkSeconds: number = Number(startConfig[1] || 60);
  const sendSeconds: number = Number(startConfig[3] || 30);
  return {
    check: Math.max(10, Number.isFinite(checkSeconds) ? checkSeconds : 60) * 1000,
    send: Math.max(5, Number.isFinite(sendSeconds) ? sendSeconds : 30) * 1000
  };
}

export class SynapAgent {
  static readonly version: string = SDK_VERSION;
  private static instance?: SynapAgent;
  private static initializePromise?: Promise<SynapAgent>;
  private static initializingConfig?: ResolvedAgentConfig;

  static async initialize(context: common.Context, config: SynapAgentConfig): Promise<SynapAgent> {
    const resolved: ResolvedAgentConfig = resolveConfig(config);
    if (SynapAgent.instance) {
      if (!sameConfig(SynapAgent.instance.config, resolved)) {
        throw new SynapError('AGENT_ALREADY_INITIALIZED',
          'Destroy the existing SynapAgent before initializing with different configuration');
      }
      return SynapAgent.instance;
    }
    if (SynapAgent.initializePromise) {
      if (!SynapAgent.initializingConfig || !sameConfig(SynapAgent.initializingConfig, resolved)) {
        throw new SynapError('AGENT_ALREADY_INITIALIZING',
          'SynapAgent is initializing with different configuration');
      }
      return SynapAgent.initializePromise;
    }
    if (!SynapAgent.initializePromise) {
      SynapAgent.initializingConfig = resolved;
      SynapAgent.initializePromise = SynapAgent.create(context, resolved)
        .catch((error: Object) => {
          throw asSynapError(error, 'AGENT_INITIALIZE_FAILED', 'Agent initialization failed');
        }).finally(() => {
          SynapAgent.initializePromise = undefined;
          SynapAgent.initializingConfig = undefined;
        });
    }
    return SynapAgent.initializePromise;
  }

  private static async create(context: common.Context,
    config: ResolvedAgentConfig): Promise<SynapAgent> {
    const store: AgentStore = config.store || new PreferencesAgentStore();
    await store.initialize(context, config.appKeyId);
    const queue: MessageQueue = new MessageQueue(store, config.queueLimit);
    await queue.restore();
    const crypto: ProtocolCryptoProvider = config.cryptoProvider || new CryptoFrameworkProvider();
    const identities: InstallationIdentityStore = config.identityStore || new HuksIdentityStore();
    const identity: InstallationIdentity = await identities.loadOrCreate(context, config.appId);
    const persistedStartId: number = Number(await store.getString(START_ID_STORAGE_KEY, '0'));
    const startId: number = Number.isSafeInteger(persistedStartId) && persistedStartId > 0 ?
      persistedStartId : Date.now();
    await store.putString(START_ID_STORAGE_KEY, String(startId));
    const persistedSequence: number = Number(await store.getString(NEXT_SEQUENCE_STORAGE_KEY, '0'));
    const nextSequence: number = Math.max(queue.highestSequence() + 1,
      Number.isSafeInteger(persistedSequence) && persistedSequence >= 2 ? persistedSequence : 2);
    const transport: Transport = config.transport || new HttpTransport();
    const secureClient: HarmonySecureClient = new HarmonySecureClient(
      context, config, transport, crypto, identities);
    const agent: SynapAgent = new SynapAgent(context, config, store, queue, crypto,
      identity, secureClient, startId, nextSequence);
    SynapAgent.instance = agent;
    return agent;
  }

  private readonly context: common.Context;
  private readonly config: ResolvedAgentConfig;
  private readonly store: AgentStore;
  private readonly queue: MessageQueue;
  private readonly crypto: ProtocolCryptoProvider;
  private readonly identity: InstallationIdentity;
  private readonly secureClient: HarmonySecureClient;
  private readonly startId: number;
  private readonly collector: HarmonyCollector;
  private status: AgentStatus = 'initialized';
  private startTracking?: StartTracking;
  private nextMessageSequence: number;
  private checkTimer?: number;
  private sendTimer?: number;
  private retryTimer?: number;
  private retryCount: number = 0;
  private flushPromise?: Promise<boolean>;
  private lifecyclePromise: Promise<void> = Promise.resolve();
  private metrics: AgentMetrics;

  private constructor(context: common.Context, config: ResolvedAgentConfig, store: AgentStore,
    queue: MessageQueue, crypto: ProtocolCryptoProvider, identity: InstallationIdentity,
    secureClient: HarmonySecureClient, startId: number, nextSequence: number) {
    this.context = context;
    this.config = config;
    this.store = store;
    this.queue = queue;
    this.crypto = crypto;
    this.identity = identity;
    this.secureClient = secureClient;
    this.startId = startId;
    this.nextMessageSequence = nextSequence;
    this.collector = new HarmonyCollector(context, config.collectionProfile,
      config.expectedSigningCertificateSha256, crypto, identity.deviceId, config.appId, this.startId);
    this.metrics = {
      status: this.status,
      transportTrust: 'sc1-authenticated/P256_HKDF_SHA256_AES256_GCM',
      queue: queue.metrics(),
      requestFailures: 0,
      parseFailures: 0,
      batchesSent: 0,
      retries: 0,
      ackFailures: 0,
      serverDuplicates: 0,
      rejectedMessages: 0,
      collectionFailures: 0,
      lastCollectionDurationMs: 0,
      lastErrorCode: ''
    };
  }

  async start(): Promise<void> {
    return this.serializeLifecycle(async () => {
      if (this.status === 'running') return;
      if (this.status === 'destroyed' || this.status === 'destroying') {
        throw new SynapError('AGENT_DESTROYED', 'SynapAgent has been destroyed');
      }
      this.setStatus('starting');
      try {
        if (this.config.enrollmentTokenProvider) {
          const token: string | undefined = await this.config.enrollmentTokenProvider.getToken();
          if (token) await this.secureClient.enroll(token);
        }
        this.startTracking = await this.loadOrCreateStartTracking();
        this.startTracking.attempt += 1;
        await this.persistStartTracking();
        const meta: StartMeta = {
          protocolVersion: PROTOCOL_VERSION,
          appKeyId: this.config.appKeyId,
          detectorVersion: SDK_VERSION,
          messageId: this.startTracking.messageId,
          batchId: this.startTracking.batchId,
          sessionId: this.startTracking.sessionId,
          attempt: this.startTracking.attempt,
          createdAt: this.startTracking.createdAt,
          observedAt: this.startTracking.observedAt,
          sequence: 1
        };
        const response: StartResponse = responseJson<StartResponse>(await this.securePost(
          '/attach/start', JSON.stringify({ common: await this.collector.common(), meta })));
        if (response.status !== 1) {
          throw new SynapError('AGENT_START_REJECTED', 'Server rejected HarmonyOS probe start');
        }
        this.startTracking.acknowledged = true;
        await this.persistStartTracking();
        this.setStatus('running');
        await this.collectOnce();
        const intervals = configIntervals(response.config);
        this.checkTimer = setInterval(() => {
          this.collectOnce().catch((error: Object) => this.recordError(error));
        }, intervals.check);
        this.sendTimer = setInterval(() => {
          this.flush().catch((error: Object) => this.recordError(error));
        }, intervals.send);
        await this.flush();
      } catch (error) {
        this.clearTimers();
        this.setStatus('initialized');
        const mapped: SynapError = asSynapError(error as Object,
          'AGENT_START_FAILED', 'Agent start failed');
        this.recordError(mapped);
        throw mapped;
      }
    });
  }

  async stop(options: StopOptions = {}): Promise<void> {
    return this.serializeLifecycle(async () => {
      if (this.status === 'destroyed' || this.status === 'stopped' || this.status === 'initialized') {
        return;
      }
      this.setStatus('stopping');
      this.clearTimers();
      if (options.flush !== false) {
        try { await this.flush(); } catch (error) { this.recordError(error as Object); }
      }
      this.clearTimers();
      await this.queue.persist();
      this.setStatus('stopped');
    });
  }

  async destroy(options: StopOptions = {}): Promise<void> {
    return this.serializeLifecycle(async () => {
      if (this.status === 'destroyed') return;
      this.setStatus('destroying');
      this.clearTimers();
      if (options.flush !== false) {
        try { await this.flush(); } catch (error) { this.recordError(error as Object); }
      }
      this.clearTimers();
      await this.queue.persist();
      this.secureClient.closeSession();
      if (this.queue.isEmpty()) {
        await this.store.delete(START_STORAGE_KEY);
        await this.store.delete(START_ID_STORAGE_KEY);
        await this.store.delete(NEXT_SEQUENCE_STORAGE_KEY);
      }
      this.setStatus('destroyed');
      SynapAgent.instance = undefined;
    });
  }

  async flush(): Promise<boolean> {
    if (this.status === 'destroyed') return false;
    if (this.flushPromise) return this.flushPromise;
    this.flushPromise = this.performFlush().catch((error: Object) => {
      throw asSynapError(error, 'AGENT_UPLOAD_FAILED', 'Agent upload failed');
    }).finally(() => this.flushPromise = undefined);
    return this.flushPromise;
  }

  private async performFlush(): Promise<boolean> {
    if (!this.startTracking?.acknowledged || this.queue.isEmpty()) return false;
    const drained: AgentMessage[] = this.queue.takeBatch(this.config.batchSize);
    if (drained.length === 0) return false;
    const common: Object[] = await this.collector.common();
    const batchNonce: string = base64UrlEncode(await this.crypto.randomBytes(12));
    const result: BatchResult = splitUploadBatches(common, drained, this.config.batchSize,
      MAX_UPLOAD_BODY_BYTES, (index: number): UploadMeta => ({
        protocolVersion: PROTOCOL_VERSION,
        batchId: `batch-${batchNonce}-${index}`,
        sessionId: String(this.startId),
        attempt: this.retryCount + 1,
        createdAt: Date.now(),
        appKeyId: this.config.appKeyId,
        detectorVersion: SDK_VERSION
      }));
    this.metrics.rejectedMessages += result.oversized.length;
    const retry: AgentMessage[] = [];
    for (let index: number = 0; index < result.batches.length; index += 1) {
      const batch: UploadBatch = result.batches[index];
      try {
        const response: UploadResponse = responseJson<UploadResponse>(await this.securePost(
          '/attach/upload', batch.body));
        if (response.status !== 1 || !response.ack) {
          this.metrics.ackFailures += 1;
          throw new SynapError('AGENT_ACK_INVALID', 'Upload acknowledgement is missing');
        }
        const ack = response.ack;
        this.metrics.serverDuplicates += ack.duplicateMessageIds.length;
        this.metrics.rejectedMessages += ack.rejectedItems.filter(item => !item.retryable).length;
        retry.push(...retryableMessages(batch, ack.batchId, ack.acceptedMessageIds,
          ack.duplicateMessageIds, ack.rejectedItems));
        this.metrics.batchesSent += 1;
      } catch (error) {
        this.metrics.requestFailures += 1;
        this.retryCount += 1;
        this.metrics.retries += 1;
        const remaining: AgentMessage[] = result.batches.slice(index)
          .reduce((all: AgentMessage[], value: UploadBatch) => all.concat(value.messages), []);
        this.queue.restoreBatch(retry.concat(remaining));
        await this.queue.persist();
        this.scheduleRetry();
        const mapped: SynapError = asSynapError(error as Object,
          'AGENT_UPLOAD_FAILED', 'Agent upload failed');
        this.recordError(mapped);
        throw mapped;
      }
    }
    this.queue.completeBatch(retry);
    if (retry.length > 0) {
      this.retryCount += 1;
      this.metrics.retries += 1;
      this.scheduleRetry();
    } else {
      this.retryCount = 0;
      if (this.retryTimer) clearTimeout(this.retryTimer);
      this.retryTimer = undefined;
    }
    await this.queue.persist();
    return true;
  }

  userData(userId: string, data: Record<string, Object>): boolean {
    if ((this.status !== 'running' && this.status !== 'stopped') ||
      !validateUserData(userId, data)) return false;
    this.collector.setUserId(userId);
    return this.enqueue('UserData', { userId, data, buildDate: Date.now() });
  }

  markAction(action: string, data?: Record<string, Object>): boolean {
    if (!this.config.collectionProfile.manualBehavior || this.status !== 'running' ||
      !validateAction(action, data)) return false;
    return this.enqueue('HarmonyBehavior', { action, data: data || {}, source: 'manual' });
  }

  async getDeviceInfo(): Promise<Record<string, Object>> {
    return this.query('/cross/info');
  }

  async getRatingInfo(): Promise<Record<string, Object>> {
    return this.query('/cross/rating');
  }

  async getLabelInfo(): Promise<Record<string, Object>> {
    return this.query('/cross/label_start');
  }

  getMetrics(): AgentMetrics {
    return {
      ...this.metrics,
      status: this.status,
      queue: this.queue.metrics()
    };
  }

  private async query(path: string): Promise<Record<string, Object>> {
    if (this.status === 'destroyed') throw new SynapError('AGENT_DESTROYED', 'SynapAgent is destroyed');
    const meta: QueryMeta = {
      protocolVersion: PROTOCOL_VERSION,
      appKeyId: this.config.appKeyId,
      detectorVersion: SDK_VERSION
    };
    const response: SecureResponse = await this.securePost(path,
      JSON.stringify({ common: await this.collector.common(), meta }));
    return responseJson<Record<string, Object>>(response);
  }

  private async collectOnce(): Promise<void> {
    const startedAt: number = Date.now();
    try {
      const facts: AgentMessage[] = await this.collector.collect(
        (type: string, payload: Record<string, Object>) => this.newMessage(type, payload));
      facts.forEach((fact: AgentMessage) => this.queue.enqueue(fact));
    } catch (error) {
      this.metrics.collectionFailures += 1;
      this.recordError(error as Object);
    } finally {
      this.metrics.lastCollectionDurationMs = Date.now() - startedAt;
      await this.store.putString(NEXT_SEQUENCE_STORAGE_KEY, String(this.nextMessageSequence));
      await this.queue.persist();
    }
  }

  private enqueue(type: string, payload: Record<string, Object>): boolean {
    const accepted: boolean = this.queue.enqueue(this.newMessage(type, payload));
    if (accepted) {
      Promise.all([
        this.queue.persist(),
        this.store.putString(NEXT_SEQUENCE_STORAGE_KEY, String(this.nextMessageSequence))
      ]).catch((error: Object) => this.recordError(error));
    }
    return accepted;
  }

  private newMessage(type: string, payload: Record<string, Object>): AgentMessage {
    return {
      type,
      messageId: `msg-${this.identity.deviceId.slice(-12)}-${Date.now()}-${this.nextMessageSequence}`,
      sequence: this.nextMessageSequence++,
      observedAt: Date.now(),
      payload
    };
  }

  private async securePost(path: string, body: string): Promise<SecureResponse> {
    return this.secureClient.request({
      method: 'POST',
      logicalPath: path,
      contentType: 'application/json',
      protectedHeaders: { code: this.headerCode() },
      body: utf8(body)
    });
  }

  private headerCode(): string {
    return `${this.identity.deviceId}-harmony-${this.config.appId}-${SDK_VERSION}`;
  }

  private async loadOrCreateStartTracking(): Promise<StartTracking> {
    const persisted: string = await this.store.getString(START_STORAGE_KEY, '');
    if (persisted.length > 0) {
      try {
        const value: StartTracking = JSON.parse(persisted) as StartTracking;
        if (value.sessionId === String(this.startId) && value.sequence === 1) return value;
      } catch (_error) {}
    }
    const now: number = Date.now();
    const suffix: string = base64UrlEncode(await this.crypto.randomBytes(16));
    return {
      messageId: `start-msg-${suffix}`,
      batchId: `start-batch-${suffix}`,
      sessionId: String(this.startId),
      attempt: 0,
      createdAt: now,
      observedAt: now,
      sequence: 1,
      acknowledged: false
    };
  }

  private async persistStartTracking(): Promise<void> {
    if (this.startTracking) {
      await this.store.putString(START_STORAGE_KEY, JSON.stringify(this.startTracking));
    }
  }

  private scheduleRetry(): void {
    if (this.retryTimer || this.status !== 'running') return;
    const base: number = Math.min(30000, 1000 * Math.pow(2, Math.min(this.retryCount, 5)));
    const delay: number = Math.round(base * (0.8 + Math.random() * 0.4));
    this.retryTimer = setTimeout(() => {
      this.retryTimer = undefined;
      this.flush().catch((error: Object) => this.recordError(error));
    }, delay);
  }

  private clearTimers(): void {
    if (this.checkTimer) clearInterval(this.checkTimer);
    if (this.sendTimer) clearInterval(this.sendTimer);
    if (this.retryTimer) clearTimeout(this.retryTimer);
    this.checkTimer = undefined;
    this.sendTimer = undefined;
    this.retryTimer = undefined;
  }

  private setStatus(status: AgentStatus): void {
    this.status = status;
    this.metrics.status = status;
  }

  private recordError(error: Object): void {
    const mapped: SynapError = asSynapError(error, 'AGENT_INTERNAL_ERROR', 'Agent operation failed');
    this.metrics.lastErrorCode = mapped.code;
  }

  private async serializeLifecycle(task: () => Promise<void>): Promise<void> {
    const previous: Promise<void> = this.lifecyclePromise;
    let release: () => void = () => {};
    this.lifecyclePromise = new Promise<void>((resolve: () => void) => release = resolve);
    await previous;
    try {
      await task();
    } catch (error) {
      throw asSynapError(error as Object, 'AGENT_LIFECYCLE_FAILED', 'Agent lifecycle failed');
    } finally {
      release();
    }
  }
}
