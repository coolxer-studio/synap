import ctRuntime from './runtime'
import ctLocation from './location';
import ctSelfApp from './selfapp'
import ctDebugMonitor from './debug'
import baseData from '../base-data';

window.ct_self_app_config = {
  "base": ["domain"]
}

window.ct_location_config = {
  "base": ["getLocation"]
}

window.ct_runtime_config = {
  "custom": ["format", "runType", "getSpider", "unknownFunctions", "infoModify"]
}

window.ct_debug_config = {
  "base": [".*"]
}

const getMethodValue = function (entryArray, conficArray) {
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

export default {

  getLocation: function () {
    return ctLocation.getLocation();
  },
  wanIp: function () {
    return '0.0.0.0';
  },
  checkSelfApp: function () {
    var data = {
      'type': 'SelfAppData',
      'buildDate':baseData.currentFormatDate()
    }
    var config = window.ct_self_app_config;
    for (var key of Object.keys(config)) {
      data[key] = getMethodValue([ctSelfApp], config[key]);
    }
    return data;
  },
  checkLocation: function () {
    var data = {
      'type': 'LocationData',
      'buildDate':baseData.currentFormatDate()
    }
    var config = window.ct_location_config;
    for (var key of Object.keys(config)) {
      data[key] = getMethodValue([ctLocation], config[key]);
    }
    return data;
  },
  checkRuntime: function () {
    var data = {
      'type': 'RuntimeData',
      'buildDate':baseData.currentFormatDate()
    }
    var config = window.ct_runtime_config;
    for (var key of Object.keys(config)) {
      data[key] = getMethodValue([ctRuntime], config[key]);
    }
    return data;
  },
  debugMonitor: function (msgCallback) {
    if (window.ct_debug_config) {
      return ctDebugMonitor(msgCallback);
    }
    return function () {};
  },
  startLocation: function () {
    return ctLocation.start();
  },
  loadConfig: function (config) {
    window.ct_self_app_config = config['selfAppConfig'];
    window.ct_location_config = config['locationConfig'];
    window.ct_runtime_config = config['runtimeConfig'];
    window.ct_debug_config = config['debugConfig'];
  }

}
