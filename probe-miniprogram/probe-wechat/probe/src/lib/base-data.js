import ctDeviceInfo from './device-info/ct-device-info';

function dateFormat(date) {
  var year = date.getFullYear();
  var month = String(date.getMonth() + 1).padStart(2, '0');
  var day = String(date.getDate()).padStart(2, '0');
  var hours = String(date.getHours()).padStart(2, '0');
  var minutes = String(date.getMinutes()).padStart(2, '0');
  var seconds = String(date.getSeconds()).padStart(2, '0');

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function getOrCreateStartId() {
  const key = '_s_d';
  var startId = wx.getStorageSync(key);
  if (startId) {
    return parseInt(startId);
  }
  startId = new Date().getTime();
  wx.setStorageSync(key, startId);
  return parseInt(startId);
}

const PLATFORM = 'wechat';
const APP_ID = 1;
const SDK_VERSION = '1.0.0.240701';

export default (function () {
  class BaseData {
    constructor() {
    }
    currentFormatDate(){
      return dateFormat(new Date());
    }
    getHeaderCode() {
      return `${ctDeviceInfo.guid()}-${PLATFORM}-${APP_ID}-${SDK_VERSION}`;
    }
    getCommonInfo() {
      const common = new Array(19).fill('');
      common[0] = wx.getStorageSync('user_id');
      common[1] = ctDeviceInfo.guid();
      common[2] = getOrCreateStartId();
      common[3] = SDK_VERSION;
      common[4] = APP_ID;
      common[5] = ctDeviceInfo.getAppName();
      common[6] = ctDeviceInfo.getCurrentPage();
      common[7] = '-';
      common[8] = PLATFORM;
      common[9] = ctDeviceInfo.manufacturer();
      common[10] = ctDeviceInfo.model();
      common[11] = ctDeviceInfo.system();
      common[12] = ctDeviceInfo.systemVersion();
      common[13] = ctDeviceInfo.netType();
      common[14] = ctDeviceInfo.lanIp();
      common[15] = "0.0.0.0";
      common[16] = null;
      common[17] = null;
      common[18] = '';
      common[19] = '';
      common[20] = '';
      common[21] = '';
      common[22] = '';
      common[23] = dateFormat(new Date());
      return common;
    }
  }

  return new BaseData();
})();

