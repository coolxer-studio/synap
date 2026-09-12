import { contextBridge, ipcRenderer } from 'electron';
import { installPreloadBridge } from './preload-runtime.js';

installPreloadBridge({ contextBridge, ipcRenderer });
