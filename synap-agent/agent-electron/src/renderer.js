import '../../agent-h5/src/index.js';
import { createRendererRuntime } from './renderer-runtime.js';

const runtime = createRendererRuntime({
  getWindow: () => window,
  getAgent: () => window.SynapAgent
});

export const initElectronAgent = runtime.initElectronAgent;
export const destroyElectronAgent = runtime.destroyElectronAgent;
