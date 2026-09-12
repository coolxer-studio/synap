// Sensitive runtime values are intentionally page-memory only. The v1 Agent
// never persists them with a public application identifier as an encryption key.
const memory = new Map();

function clone(value) {
  try { return JSON.parse(JSON.stringify(value)); } catch (_) { return value; }
}

const agLocalStore = {
  get(key) {
    const item = memory.get(String(key));
    if (!item) return null;
    if (item.expiresAt <= Date.now()) {
      memory.delete(String(key));
      return null;
    }
    return clone(item.value);
  },
  set(key, value, expire = 864e5) {
    memory.set(String(key), {
      expiresAt: Date.now() + Math.max(1, Number(expire) || 864e5),
      value: clone(value)
    });
    return true;
  },
  remove(key) {
    return memory.delete(String(key));
  },
  clear() {
    memory.clear();
  },
  hasItem(key) {
    return this.get(key) !== null;
  },
  keys() {
    return Array.from(memory.keys());
  }
};

export default agLocalStore;
