import ctUuid from './uuid'
import baseData from '../base-data'
wx.ct_device_config = {
  "device": ["abi","deviceAbi", "benchmarkLevel", "brand", "model", "system", "platform","cpuType","memorySize"],
  "screen": ["pixelRatio", "screenHeight","screenWidth","windowHeight","windowWidth","statusBarHeight","screenTop"],
  "systemSetting": ["bluetoothEnabled","deviceOrientation","locationEnabled","wifiEnabled"],
  "appBaseInfo": ["SDKVersion","enableDebug","language","version","fontSizeScaleFactor","fontSizeSetting"],
  "appAuthorizeSetting": ["albumAuthorized","bluetoothAuthorized","cameraAuthorized","locationAuthorized","locationReducedAccuracy","microphoneAuthorized","notificationAlertAuthorized","notificationAuthorized","notificationBadgeAuthorized","notificationSoundAuthorized","phoneCalendarAuthorized"],
  "battery": ["level","isCharging"],
  "uuid": ["guid"]
}

export default {

  guid: function () {
    return ctUuid.guid();
  },
  manufacturer: function () {
    return wx.getDeviceInfo().brand;
  },
  model: function () {
    return wx.getDeviceInfo().model;
  },
  system: function () {
    let system = wx.getDeviceInfo().system;
    if(system){
      let systemSplit = system.split(' ');
      return systemSplit[0];
    }else{
      return 'unknown';
    }
  },
  systemVersion: function () {
    let system = wx.getDeviceInfo().system;
    if(!system){
      return 'unknown';
    }
    let systemSplit = system.split(' ');
    if(systemSplit.length>1){
      return systemSplit[1];
    }else{
      return "-";
    }
  },
  netType: function () {
    if(getApp().networkType){
      return getApp().networkType;
    }else{
      return "unknown";
    }
  },
  lanIp: function () {
    if(getApp().localip){
      return getApp().localip;
    }else{
      return "0.0.0.0";
    }
  },
  // 获取导航栏标题
getAppName: function() {
  // return __wxConfig.global.window.navigationBarTitleText;
  return __wxConfig.accountInfo.nickname;
},
getCurrentPage: function(){
  getCurrentPages()[getCurrentPages().length - 1].route;
},
  /**
   * 设备信息
   */
  deviceInfo: function () {
    var data = {
      'type': 'DeviceData',
      'buildDate':baseData.currentFormatDate()
    };
    var getMethodValue = function (entry, conficArray) {
      var methodValueArray = [];
      for (var methodKey of conficArray) {
        var methodValue = entry[methodKey];
        if (typeof methodValue === 'function') {
          methodValue = entry[methodKey]();
        }
        methodValueArray.push(methodValue);
      }
      return methodValueArray;
    }

    var config = wx.ct_device_config;
    for (var key of Object.keys(config)) {
      switch (key) {
        case 'device':
          data[key] = getMethodValue(wx.getDeviceInfo(), config[key]);
          break;
        case 'screen':
          data[key] = getMethodValue(wx.getWindowInfo(), config[key]);
          break;
        case 'systemSetting':
          data[key] = getMethodValue(wx.getSystemSetting(), config[key]);
          break;
        case 'appBaseInfo':
            data[key] = getMethodValue(wx.getAppBaseInfo(), config[key]);
            break;
        case 'appAuthorizeSetting':
            data[key] = getMethodValue(wx.getAppAuthorizeSetting(), config[key]);
            break;
        case 'battery':
          data[key] = getMethodValue(wx.getBatteryInfoSync(), config[key]);
          break;
        case 'uuid':
          data[key] = getMethodValue(ctUuid, config[key]);
          break;
        default:
          console.error('unknown config key:' + key);
          break;
      }
    }
    return data;
  },
  loadConfig: function (config) {
    wx.ct_device_config = config['deviceConfig'];
  }
}