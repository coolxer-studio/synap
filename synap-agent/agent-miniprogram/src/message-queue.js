'use strict';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

class MessageQueue {
  constructor(options) {
    this.limit = options.limit || 50;
    this.batchLimit = options.batchLimit || 50;
    this.storage = options.storage;
    this.storageKey = options.storageKey;
    this.codec = options.codec;
    this.onDrop = options.onDrop || function noop() {};
    this.items = [];
    this.inFlight = null;
  }

  enqueue(item) {
    const snapshot = clone(item);
    const inFlightSize = this.inFlight ? this.inFlight.length : 0;
    if (inFlightSize >= this.limit && this.items.length === 0) {
      this.onDrop('capacity');
      return null;
    }
    while (this.items.length + inFlightSize >= this.limit && this.items.length) {
      this.items.shift();
      this.onDrop('capacity');
    }
    if (this.items.length + inFlightSize >= this.limit) {
      this.onDrop('capacity');
      return null;
    }
    this.items.push(snapshot);
    return snapshot.message.messageId;
  }

  prepend(items) {
    const restored = items.map(clone);
    this.items = restored.concat(this.items);
    while (this.items.length > this.limit) {
      this.items.pop();
      this.onDrop('capacity');
    }
  }

  takeBatch() {
    if (this.inFlight || !this.items.length) return null;
    const first = this.items[0];
    let count = 0;
    while (count < this.items.length && count < this.batchLimit) {
      if (this.items[count].sessionId !== first.sessionId) break;
      count += 1;
    }
    this.inFlight = this.items.splice(0, count);
    return clone(this.inFlight);
  }

  complete(acceptedIds, duplicateIds, rejectedItems) {
    if (!this.inFlight) return { confirmed: 0, restored: 0, rejected: 0 };
    const confirmed = new Set([].concat(acceptedIds || [], duplicateIds || []));
    const rejected = new Map((rejectedItems || []).map((entry) => [entry.messageId, entry]));
    const retry = [];
    let permanent = 0;
    this.inFlight.forEach((item) => {
      const id = item.message.messageId;
      if (confirmed.has(id)) return;
      const reason = rejected.get(id);
      if (reason && reason.retryable === false) {
        permanent += 1;
        this.onDrop('rejected');
        return;
      }
      retry.push(item);
    });
    const confirmedCount = this.inFlight.length - retry.length - permanent;
    this.inFlight = null;
    this.prepend(retry);
    return { confirmed: confirmedCount, restored: retry.length, rejected: permanent };
  }

  restoreInFlight() {
    if (!this.inFlight) return 0;
    const size = this.inFlight.length;
    const batch = this.inFlight;
    this.inFlight = null;
    this.prepend(batch);
    return size;
  }

  size() {
    return this.items.length + (this.inFlight ? this.inFlight.length : 0);
  }

  snapshot() {
    return clone((this.inFlight || []).concat(this.items));
  }

  persist(now) {
    if (!this.storage || !this.codec) return false;
    const payload = JSON.stringify({ savedAt: now || Date.now(), items: this.snapshot() });
    try {
      return this.storage.set(this.storageKey, this.codec.encrypt(payload));
    } catch (_) {
      return false;
    }
  }

  restore(now, maxAge) {
    if (!this.storage || !this.codec) return 0;
    const encrypted = this.storage.get(this.storageKey);
    if (!encrypted) return 0;
    this.storage.remove(this.storageKey);
    try {
      const parsed = JSON.parse(this.codec.decrypt(encrypted));
      const current = now || Date.now();
      if (!parsed.savedAt || current - parsed.savedAt > maxAge || !Array.isArray(parsed.items)) {
        parsed.items && parsed.items.forEach(() => this.onDrop('expired'));
        return 0;
      }
      const valid = parsed.items.filter((item) => item && item.message && item.message.messageId && item.common && item.sessionId);
      this.prepend(valid);
      return valid.length;
    } catch (_) {
      this.onDrop('corrupt_storage');
      return 0;
    }
  }

  clear() {
    this.items = [];
    this.inFlight = null;
    if (this.storage) this.storage.remove(this.storageKey);
  }
}

module.exports = { MessageQueue };
