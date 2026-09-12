'use strict';

class WxStorage {
  constructor(wxApi) {
    this.wx = wxApi;
  }

  get(key) {
    try {
      if (this.wx && typeof this.wx.getStorageSync === 'function') return this.wx.getStorageSync(key) || null;
    } catch (_) { /* storage is optional */ }
    return null;
  }

  set(key, value) {
    try {
      if (this.wx && typeof this.wx.setStorageSync === 'function') {
        this.wx.setStorageSync(key, value);
        return true;
      }
    } catch (_) { /* storage is optional */ }
    return false;
  }

  remove(key) {
    try {
      if (this.wx && typeof this.wx.removeStorageSync === 'function') this.wx.removeStorageSync(key);
    } catch (_) { /* storage is optional */ }
  }
}

module.exports = { WxStorage };
