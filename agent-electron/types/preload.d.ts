export interface SynapElectronHostEvent {
  readonly id: string;
  readonly name: string;
  readonly observedAt: number;
  readonly data: Readonly<Record<string, unknown>>;
}

export interface SynapElectronBridge {
  getContext(): Promise<Readonly<{
    hostContext: Readonly<Record<string, unknown>>;
    pendingEvents: readonly SynapElectronHostEvent[];
  }>>;
  ackEvents(ids: string[]): Promise<Readonly<{ acknowledged: number }>>;
  subscribe(callback: (event: SynapElectronHostEvent) => void): boolean;
  unsubscribe(): void;
}

declare global {
  interface Window {
    SynapElectronProbe: SynapElectronBridge;
  }
}

export {};
