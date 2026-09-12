import type { AgentMessage, AgentStore, QueueMetrics } from './Models';

const QUEUE_STORAGE_KEY: string = 'pending_messages_v1';

function cloneMessage(value: AgentMessage): AgentMessage {
  return JSON.parse(JSON.stringify(value)) as AgentMessage;
}

export class MessageQueue {
  private readonly limit: number;
  private readonly store: AgentStore;
  private items: AgentMessage[] = [];
  private inFlight: AgentMessage[] = [];
  private dropped: number = 0;
  private persistPromise: Promise<void> = Promise.resolve();

  constructor(store: AgentStore, limit: number) {
    this.store = store;
    this.limit = limit;
  }

  async restore(): Promise<void> {
    const serialized: string = await this.store.getString(QUEUE_STORAGE_KEY, '');
    if (serialized.length === 0) return;
    try {
      const restored: AgentMessage[] = JSON.parse(serialized) as AgentMessage[];
      if (Array.isArray(restored)) {
        const accepted: AgentMessage[] = restored.slice(-this.limit).map(cloneMessage);
        this.items.push(...accepted);
        this.dropped += Math.max(0, restored.length - accepted.length);
      }
    } catch (_error) {
      // Keep the persisted copy intact. A later successful persist replaces it; deleting it
      // during restore would lose retry data if initialization is interrupted afterwards.
    }
  }

  enqueue(message: AgentMessage): boolean {
    if (this.items.length + this.inFlight.length >= this.limit) {
      this.dropped += 1;
      return false;
    }
    this.items.push(cloneMessage(message));
    return true;
  }

  takeBatch(size: number): AgentMessage[] {
    if (this.inFlight.length > 0) {
      return this.inFlight.map(cloneMessage);
    }
    this.inFlight = this.items.splice(0, Math.max(0, size));
    return this.inFlight.map(cloneMessage);
  }

  restoreBatch(messages?: AgentMessage[]): void {
    const restored: AgentMessage[] = (messages || this.inFlight).map(cloneMessage);
    const combined: AgentMessage[] = restored.concat(this.items);
    this.items = combined.slice(0, this.limit);
    this.dropped += Math.max(0, combined.length - this.items.length);
    this.inFlight = [];
  }

  completeBatch(retryMessages: AgentMessage[] = []): void {
    const combined: AgentMessage[] = retryMessages.map(cloneMessage).concat(this.items);
    this.items = combined.slice(0, this.limit);
    this.dropped += Math.max(0, combined.length - this.items.length);
    this.inFlight = [];
  }

  async persist(): Promise<void> {
    const operation: Promise<void> = this.persistPromise.then(async () => {
      const pending: AgentMessage[] = this.inFlight.concat(this.items).map(cloneMessage);
      if (pending.length === 0) {
        await this.store.delete(QUEUE_STORAGE_KEY);
      } else {
        await this.store.putString(QUEUE_STORAGE_KEY, JSON.stringify(pending));
      }
    });
    this.persistPromise = operation.catch((_error: Object) => {});
    return operation;
  }

  isEmpty(): boolean {
    return this.items.length === 0 && this.inFlight.length === 0;
  }

  highestSequence(): number {
    return this.inFlight.concat(this.items)
      .reduce((maximum: number, item: AgentMessage) => Math.max(maximum, item.sequence), 1);
  }

  metrics(): QueueMetrics {
    return {
      queued: this.items.length,
      inFlight: this.inFlight.length,
      dropped: this.dropped
    };
  }
}
