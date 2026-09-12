'use strict';

const { SDK_VERSION, PLATFORM } = require('./config');
const { routeTemplate } = require('./privacy');
const { uuid } = require('./id');

function callSync(wxApi, name, fallback) {
  try {
    return typeof wxApi[name] === 'function' ? wxApi[name]() || fallback : fallback;
  } catch (_) {
    return fallback;
  }
}

function callAsync(wxApi, name, options) {
  return new Promise((resolve) => {
    if (!wxApi || typeof wxApi[name] !== 'function') {
      resolve({ state: 'unavailable', value: null });
      return;
    }
    try {
      wxApi[name](Object.assign({}, options || {}, {
        success: (value) => resolve({ state: 'available', value: value || {} }),
        fail: (error) => resolve({ state: /auth|permission|deny/i.test(String(error && error.errMsg)) ? 'blocked' : 'error', value: null })
      }));
    } catch (_) {
      resolve({ state: 'error', value: null });
    }
  });
}

function getOrCreate(storage, key, factory) {
  const existing = storage.get(key);
  if (existing) return existing;
  const created = factory();
  storage.set(key, created);
  return created;
}

function splitSystem(system) {
  const value = String(system || 'unknown').trim().split(/\s+/);
  return { name: value[0] || 'unknown', version: value.slice(1).join(' ') || '-' };
}

function isPrivateAddress(value) {
  const address = String(value || '').trim();
  if (/^10\./.test(address) || /^192\.168\./.test(address) || /^127\./.test(address)) return true;
  const match = /^172\.(\d{1,3})\./.exec(address);
  if (match && Number(match[1]) >= 16 && Number(match[1]) <= 31) return true;
  return /^f[cd][0-9a-f]{2}:/i.test(address) || /^fe80:/i.test(address);
}

class DeviceCollector {
  constructor(options) {
    this.wx = options.wx;
    this.storage = options.storage;
    this.options = options.options;
    this.guid = getOrCreate(this.storage, '_synap_wechat_guid', () => uuid(this.wx));
    this.startId = Date.now();
    this.account = callSync(this.wx, 'getAccountInfoSync', {});
    this.device = callSync(this.wx, 'getDeviceInfo', callSync(this.wx, 'getSystemInfoSync', {}));
    this.window = callSync(this.wx, 'getWindowInfo', callSync(this.wx, 'getSystemInfoSync', {}));
    this.systemSetting = callSync(this.wx, 'getSystemSetting', {});
    this.appBaseInfo = callSync(this.wx, 'getAppBaseInfo', {});
    this.authorize = this.options.capabilities.permission ? callSync(this.wx, 'getAppAuthorizeSetting', {}) : {};
    this.battery = callSync(this.wx, 'getBatteryInfoSync', {});
    this.launch = callSync(this.wx, 'getLaunchOptionsSync', {});
    this.network = { state: 'unavailable', value: null };
    this.localAddress = { state: this.options.capabilities.localAddress ? 'unavailable' : 'disabled', value: null };
    this.location = { state: this.options.capabilities.location ? 'unavailable' : 'disabled', value: null };
  }

  async prepare() {
    this.network = await callAsync(this.wx, 'getNetworkType');
    if (this.options.capabilities.localAddress) {
      const result = await callAsync(this.wx, 'getLocalIPAddress');
      const address = result.value && (result.value.localip || result.value.localIp || result.value.ip);
      this.localAddress = result.state === 'available' && isPrivateAddress(address)
        ? { state: 'available', value: address }
        : { state: result.state === 'available' ? 'unavailable' : result.state, value: null };
    }
    if (this.options.capabilities.location) {
      const authorization = this.authorize.locationAuthorized;
      if (authorization === 'authorized') {
        const result = await callAsync(this.wx, 'getLocation', { type: 'wgs84', isHighAccuracy: false });
        this.location = result.state === 'available'
          ? { state: 'available', value: result.value }
          : { state: result.state, value: null };
      } else {
        this.location = { state: authorization === 'denied' ? 'blocked' : 'disabled', value: null };
      }
    }
    return this;
  }

  miniProgramInfo() {
    return this.account.miniProgram || {};
  }

  common(userId) {
    const mini = this.miniProgramInfo();
    const system = splitSystem(this.device.system);
    const location = this.location.value || {};
    const networkType = this.network.value && this.network.value.networkType || 'unknown';
    return [
      String(userId || ''),
      this.guid,
      String(this.startId),
      SDK_VERSION,
      String(this.options.appId),
      this.options.appName,
      String(mini.appId || ''),
      String(mini.version || ''),
      PLATFORM,
      String(this.device.brand || ''),
      String(this.device.model || ''),
      system.name,
      system.version,
      String(networkType),
      this.localAddress.value || '',
      '',
      location.latitude == null ? '' : String(location.latitude),
      location.longitude == null ? '' : String(location.longitude),
      '', '', '', '', '',
      new Date().toISOString()
    ];
  }

  headerCode() {
    return `${this.guid}-${PLATFORM}-${this.options.appId}-${SDK_VERSION}`;
  }

  deviceFact() {
    const d = this.device;
    const w = this.window;
    const s = this.systemSetting;
    const a = this.appBaseInfo;
    const p = this.options.capabilities.permission ? this.authorize : {};
    const b = this.battery;
    const permission = this.options.capabilities.permission ? 'available' : 'disabled';
    return {
      type: 'DeviceData',
      buildDate: new Date().toISOString(),
      device: [d.abi || '', d.deviceAbi || '', Number(d.benchmarkLevel) || 0, d.brand || '', d.model || '', d.system || '', d.platform || '', d.cpuType || '', String(d.memorySize || '')],
      screen: [Math.round(Number(w.pixelRatio) || 0), Math.round(Number(w.screenHeight) || 0), Math.round(Number(w.screenWidth) || 0), Math.round(Number(w.windowHeight) || 0), Math.round(Number(w.windowWidth) || 0), Math.round(Number(w.statusBarHeight) || 0), Math.round(Number(w.screenTop) || 0)],
      systemSetting: [Boolean(s.bluetoothEnabled), s.deviceOrientation || '', Boolean(s.locationEnabled), Boolean(s.wifiEnabled)],
      appBaseInfo: [a.SDKVersion || '', Boolean(a.enableDebug), a.language || '', a.version || '', Math.round(Number(a.fontSizeScaleFactor) || 0), Math.round(Number(a.fontSizeSetting) || 0)],
      appAuthorizeSetting: [p.albumAuthorized || '', p.bluetoothAuthorized || '', p.cameraAuthorized || '', p.locationAuthorized || '', p.locationReducedAccuracy || '', p.microphoneAuthorized || '', p.notificationAlertAuthorized || '', p.notificationAuthorized || '', p.notificationBadgeAuthorized || '', p.notificationSoundAuthorized || '', p.phoneCalendarAuthorized || ''],
      battery: [String(b.level == null ? '' : b.level), Boolean(b.isCharging)],
      uuid: [this.guid],
      capabilities: {
        device: typeof this.wx.getDeviceInfo === 'function' || typeof this.wx.getSystemInfoSync === 'function' ? 'available' : 'unavailable',
        window: typeof this.wx.getWindowInfo === 'function' || typeof this.wx.getSystemInfoSync === 'function' ? 'available' : 'unavailable',
        systemSetting: typeof this.wx.getSystemSetting === 'function' ? 'available' : 'unavailable',
        appBaseInfo: typeof this.wx.getAppBaseInfo === 'function' ? 'available' : 'unavailable',
        battery: typeof this.wx.getBatteryInfoSync === 'function' ? 'available' : 'unavailable',
        network: this.network.state,
        localAddress: this.localAddress.state,
        permission
      }
    };
  }

  selfAppFact() {
    const mini = this.miniProgramInfo();
    const referrer = this.launch.referrerInfo || {};
    return {
      type: 'SelfAppData',
      buildDate: new Date().toISOString(),
      data: {
        appId: mini.appId || '',
        version: mini.version || '',
        envVersion: mini.envVersion || '',
        sdkVersion: SDK_VERSION,
        sdkRuntimeVersion: this.appBaseInfo.SDKVersion || '',
        scene: Number(this.launch.scene) || 0,
        referrerAppId: referrer.appId || '',
        path: routeTemplate(),
        extraDataState: referrer.extraData ? 'masked' : 'unavailable'
      }
    };
  }

  locationFact() {
    const value = this.location.value;
    if (!value) return null;
    return {
      type: 'LocationData',
      buildDate: new Date().toISOString(),
      data: {
        status: this.location.state,
        latitude: Number(value.latitude),
        longitude: Number(value.longitude),
        accuracy: Number(value.accuracy) || 0,
        speed: Number(value.speed) || 0,
        observedAt: Date.now()
      }
    };
  }
}

module.exports = { DeviceCollector, callAsync, isPrivateAddress, splitSystem };
