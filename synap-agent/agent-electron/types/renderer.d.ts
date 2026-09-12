export type CollectionProfileName = 'low' | 'low-intrusion' | 'balanced' | 'full' | 'minimal';

export interface ElectronAgentOptions {
  baseUrl: string;
  appId: string;
  serverTrustAnchors: Record<string, string>;
  collectionProfile?: CollectionProfileName | Record<string, boolean>;
  requestTimeout?: number;
  queueLimit?: number;
  batchSize?: number;
  maxUserDataBytes?: number;
  allowedScriptOrigins?: string[];
  allowedParentOrigins?: string[];
  allowedServiceWorkerOrigins?: string[];
  expectedScriptIntegrity?: string;
}

export interface SynapElectronAgent {
  readonly version: string;
  init(options: ElectronAgentOptions): Promise<SynapElectronAgent>;
  destroy(options?: { flush?: boolean }): Promise<void>;
  flush(): Promise<boolean>;
  userData(userId: string, data: Record<string, unknown>): boolean;
  markAction(action: unknown): boolean;
  recordEvent(name: string, data?: Record<string, unknown>): boolean;
  getDeviceInfo(): Promise<unknown>;
  getRatingInfo(): Promise<unknown>;
  getLabelInfo(): Promise<unknown>;
  getMetrics(): Readonly<Record<string, unknown>>;
}

export function initElectronAgent(options: ElectronAgentOptions): Promise<SynapElectronAgent>;
export function destroyElectronAgent(options?: { flush?: boolean }): Promise<void>;
