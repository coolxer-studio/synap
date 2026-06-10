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
    return '0.0.0.0';
  },
  /**
   * 设备信息
   */
  deviceInfo: function () {
    var data = {
      'type': 'DeviceData',
      'buildDate':baseData.currentFormatDate()
    };
    var getMethodValue = function (entryArray, conficArray) {
      var methodValueArray = [];
      for (var methodKey of conficArray) {
        var methodValue;
        for (var entry of entryArray) {
          var methodValue = entry[methodKey];
          if (typeof methodValue === 'function') {
            methodValue = entry[methodKey]();
          }
          if (methodValue) {
            break;
          }
        }
        methodValueArray.push(methodValue);
      }
      return methodValueArray;
    }

    var config = window.ct_device_config;
    for (var key of Object.keys(config)) {
      switch (key) {
        case 'device':
          data[key] = getMethodValue([ctDevice], config[key]);
          break;
        case 'browser':
          data[key] = getMethodValue([ctBrowser], config[key]);
          break;
        case 'screen':
          data[key] = getMethodValue([ctScreen, screen], config[key]);
          break;
        case 'cpu':
          data[key] = getMethodValue([ctCpu], config[key]);
          break;
        case 'battery':
          data[key] = getMethodValue([ctBattery], config[key]);
          break;
        case 'uuid':
          data[key] = getMethodValue([ctUuid], config[key]);
          break;
        case 'network':
          data[key] = getMethodValue([ctNetwork], config[key]);
          break;
        case 'storage':
          data[key] = getMethodValue([ctStorage], config[key]);
          break;
        case 'baseInfo':
          data[key] = getMethodValue([ctBaseInfo], config[key]);
          break;
        case 'document':
          data[key] = getMethodValue([document], config[key]);
          break;
        case 'window':
          data[key] = getMethodValue([window], config[key]);
          break;
        case 'navigator':
          data[key] = getMethodValue([navigator], config[key]);
          break;
        default:
          console.error('unknown config key:' + key);
          break;
      }
    }
    return data;
  },
  loadConfig: function (config) {
    window.ct_device_config = config['deviceConfig'];
  }
}