function uuidv4() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        return window.crypto.randomUUID();
    }
    const bytes = new Uint8Array(16);
    if (window.crypto && typeof window.crypto.getRandomValues === 'function') {
        window.crypto.getRandomValues(bytes);
    } else {
        for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
    }
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export default {

    /**
     * 封装一个方法，用来查询并生成 UUID
     * @returns UUID
     */
    guid: function () {
        const key = '_g_d';
        try {
            const storedGuid = window.localStorage.getItem(key);
            if (storedGuid) return storedGuid;
            const newGuid = uuidv4();
            window.localStorage.setItem(key, newGuid);
            return newGuid;
        } catch (_) {
            if (!window.__synapMemoryGuid) window.__synapMemoryGuid = uuidv4();
            return window.__synapMemoryGuid;
        }
    }
}
