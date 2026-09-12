import type { BrowserWindow } from 'electron';

export interface ElectronAgentMainOptions {
  allowedOrigins: string[];
}

export interface ElectronAgentMainMetrics {
  emitted: number;
  acknowledged: number;
  rejectedIpc: number;
  queuedEvents: number;
}

export interface ElectronAgentRegistration {
  dispose(): void;
  getMetrics(): Readonly<ElectronAgentMainMetrics>;
}

export function registerElectronAgent(
  browserWindow: BrowserWindow,
  options: ElectronAgentMainOptions
): ElectronAgentRegistration;
