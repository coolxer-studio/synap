import ctBrowser from './browser'
import ctBaseInfo from './base-info'
import ctUuid from './uuid'
import ctNetwork from './network'
import ctDevice from './device'
import ctCpu from './cpu'
import ctStorage from './storage'
import ctScreen from './screen'
import ctBattery from './battery'
import baseData from '../base-data'
import { collectConfiguredValues } from './collection-policy'

window.ct_device_config = {
  "device": ["systemName", "systemVersion", "modelCode", "language", "timeZone"],
  "screen": ["width", "height", "availWidth", "availHeight", "screenSize"],
  "cpu": ["totalCore", "type", "abi"],
  "battery": ["info"],
  "uuid": ["guid"],
  "network": ["lanIp", "type", "info"],
  "storage": ["totalDisk", "totalRam"],
  "browser": ["type", "playType"],
  "baseInfo": ["getPixelRatio", "colorDepth", "getPlugins", "getAcceptMimeType", "getCanvasFpCRC", "getWebglVendorAndRenderer", "getNavigatorCpuClass", "getNavigatorPlatform", "getFonts", "getTouchSupport", "getDoNotTrack", "getAdBlock", "hasSessionStorage", "hasLocalStorage", "hasIndexedDB", "getHasLiedLanguages", "getHasLiedResolution", "getHasLiedOs", "getHasLiedBrowser", "getWebglFp", "getEmptyEvalLength", "getProductSub", "getErrorFF"],
  "document": ["characterSet"],
  "window": [""],
  "navigator": ["userAgent", "oscpu", "vendor"]
}

let collectionProfile = {};

function capabilityState(enabled, supported = true, extra = {}) {
  return { status: enabled ? (supported ? 'available' : 'unavailable') : 'disabled', ...extra };
}

export default {

  guid: function () {
    return ctUuid.guid();
  },
  manufacturer: function () {
    return ctDevice.manufacturer();
  },
  model: function () {
    return ctDevice.modelCode();
  },
  system: function () {
    return ctDevice.systemName();
  },
  systemVersion: function () {
    return ctDevice.systemVersion();
  },
  netType: function () {
    return ctNetwork.type();
  },
  lanIp: function () {
    return ctNetwork.lanIp();
  },
  detectLanIp: function (options) {
    return ctNetwork.detectLanIp(options);
  },
  getLanIpState: function () {
    return ctNetwork.getLanIpState();
  },
  prepare: async function (options = {}) {
    collectionProfile = options.collectionProfile || collectionProfile;
    const timeout = Number(options.timeout) || 1000;
    await Promise.all([
      ctNetwork.detectLanIp({ enabled: !!collectionProfile.webrtc, timeout }),
      ctBattery.prepare(timeout),
      ctStorage.prepare(timeout)
    ]);
  },
  /**
   * 设备信息
   */
  deviceInfo: function () {
    var data = {
      'type': 'DeviceData',
      'buildDate':baseData.currentFormatDate()
    };
    var config = window.ct_device_config;
    var getMethodValue = function (entryArray, section, invokeFunctions = true) {
      return collectConfiguredValues(entryArray, config[section], {
        section,
        profile: collectionProfile,
        invokeFunctions
      });
    }
    for (var key of Object.keys(config)) {
      switch (key) {
        case 'device':
          data[key] = getMethodValue([ctDevice], key);
          break;
        case 'browser':
          data[key] = getMethodValue([ctBrowser], key);
          break;
        case 'screen':
          data[key] = getMethodValue([ctScreen, screen], key);
          break;
        case 'cpu':
          data[key] = getMethodValue([ctCpu], key);
          break;
        case 'battery':
          data[key] = getMethodValue([ctBattery], key);
          break;
        case 'uuid':
          data[key] = getMethodValue([ctUuid], key);
          break;
        case 'network':
          data[key] = getMethodValue([ctNetwork], key);
          break;
        case 'storage':
          data[key] = getMethodValue([ctStorage], key);
          break;
        case 'baseInfo':
          data[key] = getMethodValue([ctBaseInfo], key);
          break;
        case 'document':
          data[key] = getMethodValue([document], key, false);
          break;
        case 'window':
          data[key] = getMethodValue([window], key, false);
          break;
        case 'navigator':
          data[key] = getMethodValue([navigator], key, false);
          break;
        default:
          console.error('unknown config key:' + key);
          break;
      }
    }
    data.capabilities = {
      battery: ctBattery.capability(),
      storageEstimate: ctStorage.capability(),
      localAddress: ctNetwork.getLanIpState(),
      canvas: capabilityState(!!collectionProfile.canvas, typeof HTMLCanvasElement !== 'undefined'),
      webgl: capabilityState(!!collectionProfile.webgl, typeof WebGLRenderingContext !== 'undefined'),
      fonts: capabilityState(!!collectionProfile.fonts, typeof document.fonts !== 'undefined'),
      geolocation: capabilityState(!!collectionProfile.location, 'geolocation' in navigator)
    };
    return data;
  },
  loadConfig: function (config, profile) {
    window.ct_device_config = config['deviceConfig'];
    collectionProfile = profile || collectionProfile;
  }
}
