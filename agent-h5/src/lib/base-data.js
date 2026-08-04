import cookieOperate from './cookie-operate'
import ctDeviceInfo from './device-info/ct-device-info';
import ctRunningCheck from './running-check/ct-running-check';
import agLocalStore from "./ag-localstore";
import { APP_ID, COMMON_FIELD_COUNT, PLATFORM, SDK_VERSION } from '../config';

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
  var startId = cookieOperate.getFromSessionCookie(key);
  if (startId) {
    return parseInt(startId);
  }
  startId = new Date().getTime();
  cookieOperate.setToSessionCookie(key, startId);
  return parseInt(startId);
}

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
      const common = new Array(COMMON_FIELD_COUNT).fill('');
      common[0] = agLocalStore.get('user_id');
      common[1] = ctDeviceInfo.guid();
      common[2] = getOrCreateStartId();
      common[3] = SDK_VERSION;
      common[4] = APP_ID;
      common[5] = document.title;
      common[6] = window.location.hostname;
      common[7] = '-';
      common[8] = PLATFORM;
      common[9] = ctDeviceInfo.manufacturer();
      common[10] = ctDeviceInfo.model();
      common[11] = ctDeviceInfo.system();
      common[12] = ctDeviceInfo.systemVersion();
      common[13] = ctDeviceInfo.netType();
      common[14] = ctDeviceInfo.lanIp();
      common[15] = ctRunningCheck.wanIp();
      // ctRunningCheck.getLocation();
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
