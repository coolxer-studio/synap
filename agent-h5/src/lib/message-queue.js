function cloneMessage(message) {
  return JSON.parse(JSON.stringify(message));
}

export default class MessageQueue {
  constructor({ storage, storageKey, limit = 50, initialItems = [] } = {}) {
    this.storage = storage;
    this.storageKey = storageKey || '__synap_pending_messages';
    this.limit = limit;
    this.items = [];
    this.dropped = 0;
    this.restorePersisted();
    initialItems.forEach((item) => this.enqueue(item));
  }

  enqueue(message) {
    if (!message) return false;
    let snapshot;
    try {
      snapshot = cloneMessage(message);
    } catch (_) {
      this.dropped += 1;
      return false;
    }
    if (this.items.length >= this.limit) {
      this.dropped += 1;
      return false;
    }
    this.items.push(snapshot);
    return true;
  }

  takeBatch(size = this.limit) {
    return this.items.splice(0, Math.max(0, size));
  }

  restoreBatch(batch) {
    if (!Array.isArray(batch) || batch.length === 0) return;
    const combined = batch.concat(this.items);
    this.items.splice(0, this.items.length, ...combined.slice(0, this.limit));
    this.dropped += Math.max(0, combined.length - this.limit);
  }

  persist() {
    if (!this.storage) return false;
    try {
      if (this.items.length > 0) {
        this.storage.setItem(this.storageKey, JSON.stringify(this.items));
      } else {
        this.storage.removeItem(this.storageKey);
      }
      return true;
    } catch (_) {
      return false;
    }
  }

  restorePersisted() {
    if (!this.storage) return;
    try {
      const serialized = this.storage.getItem(this.storageKey);
      if (!serialized) return;
      const persisted = JSON.parse(serialized);
      if (Array.isArray(persisted)) {
        persisted.slice(-this.limit).forEach((item) => this.items.push(item));
        this.dropped += Math.max(0, persisted.length - this.limit);
      }
      this.storage.removeItem(this.storageKey);
    } catch (_) {
      try { this.storage.removeItem(this.storageKey); } catch (ignore) { }
    }
  }

  metrics() {
    return { queued: this.items.length, dropped: this.dropped };
  }
}
