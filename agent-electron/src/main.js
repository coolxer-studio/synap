import os from 'node:os';
import { app, ipcMain } from 'electron';
import { createMainRuntime } from './main-runtime.js';

const runtime = createMainRuntime({
  app,
  ipcMain,
  runtimeInfo: {
    platform: process.platform,
    arch: process.arch,
    release: os.release(),
    versions: {
      electron: process.versions.electron || '',
      chrome: process.versions.chrome || '',
      node: process.versions.node || ''
    }
  }
});

export const registerElectronAgent = runtime.registerElectronAgent;
