import type { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import type { AgentStore } from '../core/Models';
import { SynapError } from '../core/SynapError';

export class PreferencesAgentStore implements AgentStore {
  private instance?: preferences.Preferences;

  async initialize(context: common.Context, namespace: string): Promise<void> {
    try {
      this.instance = await preferences.getPreferences(context, {
        name: `synap_agent_${namespace}`
      });
    } catch (error) {
      throw new SynapError('AGENT_STORAGE_FAILED', 'Could not open agent preferences',
        0, undefined, error as Object);
    }
  }

  async getString(key: string, fallback: string = ''): Promise<string> {
    if (!this.instance) {
      throw new SynapError('AGENT_STORAGE_FAILED', 'Agent preferences are not initialized');
    }
    const value: preferences.ValueType = await this.instance.get(key, fallback);
    return typeof value === 'string' ? value : fallback;
  }

  async putString(key: string, value: string): Promise<void> {
    if (!this.instance) {
      throw new SynapError('AGENT_STORAGE_FAILED', 'Agent preferences are not initialized');
    }
    await this.instance.put(key, value);
    await this.instance.flush();
  }

  async delete(key: string): Promise<void> {
    if (!this.instance) {
      throw new SynapError('AGENT_STORAGE_FAILED', 'Agent preferences are not initialized');
    }
    await this.instance.delete(key);
    await this.instance.flush();
  }
}

export class MemoryAgentStore implements AgentStore {
  private values: Map<string, string> = new Map<string, string>();

  async initialize(_context: common.Context, _namespace: string): Promise<void> {}

  async getString(key: string, fallback: string = ''): Promise<string> {
    return this.values.get(key) ?? fallback;
  }

  async putString(key: string, value: string): Promise<void> {
    this.values.set(key, value);
  }

  async delete(key: string): Promise<void> {
    this.values.delete(key);
  }
}

