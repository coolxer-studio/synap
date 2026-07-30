import { createH5Codec } from '@coolxer/secure-communication-js'
import httpAjax from './lib/secure-communication/ajax-http'
import cookieOperate from './lib/cookie-operate'
import agLocalStore from "./lib/ag-localstore";
import baseData from './lib/base-data'
import ctDeviceInfo from './lib/device-info/ct-device-info'
import ctRunningCheck from './lib/running-check/ct-running-check';
import ctIllegalState from './lib/illegal-state/ct-illegal-state'
import ctUserBehavior from './lib/user-behavior/ct-user-behavior'


// 触发发送消息
var startSender = function (cancelTime) {
  var sendStartTime = cookieOperate.getFromSessionCookie('send_start_time');
  if (sendStartTime) {
    // 超过发送周期不发送了
    var currentTime = new Date().getTime();
    if (currentTime - sendStartTime > cancelTime * 1000) {
      return;
    }
  } else {
    cookieOperate.setToSessionCookie('send_start_time', new Date().getTime());
  }
  // 开始发送
  const msgArray = window.probeMsgQueue;
  if (msgArray.length === 0) {
    // 队列为空不需要发送数据
    return;
  }
  const body = {
    "common": baseData.getCommonInfo(),
    "data": msgArray
  };
  var header = {
    'Content-Type': 'application/json;charset=UTF-8',
    'code': baseData.getHeaderCode()
  }
  const codec = createH5Codec(window.kk);
  const postData = codec.encodeRequest(JSON.stringify(body));
  httpAjax.http_post(window.up, header, postData, function (data) {
    window.probeMsgQueue = [];
    if (data && data.length > 0) {
      var responseConfig = JSON.parse(codec.decodeResponse(data));
    } else {
      console.warn('触发异常行为,不要进行非法操作!')
    }
  }, function (error) {
    console.error("上报请求失败:(" + error + ")");
  }, true);
};

function launchProbe() {
  // 初始化SDK
  console.log('synap running:true');
  // 添加事件函数
  var pushMsg = function (ep) {
    if (ep) {
      if (typeof window.probeMsgQueue === 'undefined') {
        window.probeMsgQueue = [];
      }
      if (window.probeMsgQueue.length < 50) {
        window.probeMsgQueue.push(ep);
      } else {
        console.warn('queue is to large:' + window.probeMsgQueue.length);
      }
    }
  };
  // 配置
  ctDeviceInfo.loadConfig(window._polic['config']);
  ctRunningCheck.loadConfig(window._polic['config']);
  ctIllegalState.loadConfig(window._polic['config']);
  ctUserBehavior.loadConfig(window._polic['config']);
  // 周期检测数据
  setInterval(function (cancelTime) {
    var checkStartTime = cookieOperate.getFromSessionCookie('check_start_time');
    if (checkStartTime) {
      // 超过检测周期不检测了
      var currentTime = new Date().getTime();
      if (currentTime - checkStartTime > cancelTime * 1000) {
        return;
      }
    } else {
      cookieOperate.setToSessionCookie('check_start_time', new Date().getTime());
    }
    // 开始
    pushMsg(ctDeviceInfo.deviceInfo());
    pushMsg(ctRunningCheck.checkSelfApp());
    pushMsg(ctRunningCheck.checkLocation());
    pushMsg(ctRunningCheck.checkRuntime());
  }, window._polic.config.startConfig[2] * 1000, window._polic.config.startConfig[3])

  // 回调数据
  // debug
  ctRunningCheck.debugMonitor(pushMsg);
  // is
  ctIllegalState.monitor(pushMsg);
  // ub
  ctUserBehavior.devToolMonitor(pushMsg);
  ctUserBehavior.eventMonitor(pushMsg);
  // 周期发送数据
  setInterval(startSender, window._polic.config.startConfig[4] * 1000, window._polic.config.startConfig[5]);

  // 暴露用户数据接口
  var userData = function (userId, data) {
    // userId存储
    agLocalStore.set('user_id', userId, 0);
    var userDataMsg = {
      type: 'UserData',
      userId: userId,
      data: data
    }
    pushMsg(userDataMsg);
  }
  window.userData = userData;
}


const startWithPolic = function (dispatch, config) {
  window._polic = JSON.parse(createH5Codec(dispatch.appId()).decodeResponse(config));
  window.kk = dispatch.appId();
  window.up = dispatch.upload();
  window.deviceInfoUrl = dispatch.deviceInfo();
  window.labelUrl = dispatch.label();
  window.ratingUrl = dispatch.rating();

  if (window._polic) {
    launchProbe()
  }
}

class Loader {
  constructor() {
  }
  init(dispatch) {
    if (dispatch.appId() && dispatch.appId().length > 0) {
      createH5Codec(dispatch.appId());
      const exDate = new Date();
      try {
        window.sessionStorage.setItem('_t', exDate.getTime());
      } catch (e) {
        if (navigator.cookieEnabled) {
          cookieOperate.setItem('_t', exDate.getTime(), exDate.toGMTString(), '/', [window.location.hostname]);
        } else {
          if (window.esl) {
            window.esl['_t'] = exDate.getTime();
          } else {
            window.esl = {};
            window.esl['_t'] = exDate.getTime();
          }
        }
      }
      exDate.setDate(exDate.getDate() + 1000);
      cookieOperate.setItem('accept_hex', escape(dispatch.appId()), exDate.toGMTString(), '/', [window.location.hostname]);

    } else {
      throw 'parameters of the abnormal';
    }
  }
  start(dispatch) {
    const body = {
      'common': baseData.getCommonInfo()
    };
    var header = {
      'Content-Type': 'application/json;charset=UTF-8',
      'code': baseData.getHeaderCode()
    };
    const codec = createH5Codec(dispatch.appId());
    const encBody = codec.encodeRequest(JSON.stringify(body));
    httpAjax.http_post(dispatch.start(), header, encBody,
      function (data) {
        if (data) {
          startWithPolic(dispatch, data);
          cookieOperate.setToSessionCookie('_polic', data);
        }
      },
      function (error) {
        console.error(error);
      },
      true);
  }
  indexInfo(type) {
    var url;
    switch (type) {
      case 1:
        url = window.deviceInfoUrl;
        break;
      case 2:
        url = window.labelUrl;
        break;
      case 3:
        url = window.ratingUrl;
        break;
      default:
        return "error type";
    }
    const body = {
      'common': baseData.getCommonInfo()
    };
    const codec = createH5Codec(window.kk);
    const encBody = codec.encodeRequest(JSON.stringify(body));
    var header = {
      'Content-Type': 'application/json;charset=UTF-8',
      'code': baseData.getHeaderCode()
    };
    var requestData;
    httpAjax.http_post(url, header, encBody,
      function (data) {
        if (data) {
          requestData = JSON.parse(codec.decodeResponse(data));;
        }
      },
      function (error) {
        console.error(error);
      },
      false);
    return requestData;
  }
}


function encodeString(str) {
  let encodedStr = '';
  for (let char of str) {
    encodedStr += String.fromCharCode(char.charCodeAt(0) + 1);
  }
  return encodedStr;
}

function decodeString(str) {
  let decodedStr = '';
  for (let char of str) {
    decodedStr += String.fromCharCode(char.charCodeAt(0) - 1);
  }
  return decodedStr;
}

//  let array = ['http://127.0.0.1:11099','/sc/h5/attach/start', '/sc/h5/attach/upload', '/sc/h5/cross/info', '/sc/h5/cross/rating', '/sc/h5/cross/label_start'];
//  for (let i = 0; i < array.length; i++) {
//    var original = array[i];
//    var encoded = encodeString(original);
//    console.log("Encoded: ", encoded);
//    var decoded = decodeString(encoded);
//    console.log("Decoded: ", decoded);
//  }

const ctScArray = ["iuuq;00238/1/1/2;221::","0td0i60buubdi0tubsu","0td0i60buubdi0vqmpbe","0td0i60dsptt0jogp","0td0i60dsptt0sbujoh","0td0i60dsptt0mbcfm`tubsu"];
  
class Dispatch {
  constructor(keyString) {
    if (!(this instanceof Dispatch)) {
      return new Dispatch(keyString);
    }
    this.key = keyString;
  }
  appId() {
    return this.key;
  }
  start() {
    return decodeString(ctScArray[0])+decodeString(ctScArray[1]);
  }
  upload() {
    return decodeString(ctScArray[0])+decodeString(ctScArray[2]);
  }
  deviceInfo() {
    return decodeString(ctScArray[0])+decodeString(ctScArray[3]);
  }
  rating() {
    return decodeString(ctScArray[0])+decodeString(ctScArray[4]);
  }
  label() {
    return decodeString(ctScArray[0])+decodeString(ctScArray[5]);
  }
}

const checkLoader = (function () {
  return new Loader();
})();

// main
var keyString = '2396641245c4fe6ttd041565b3651245'
var dispatch = new Dispatch(keyString);
window.deviceInfo = function () {
  return checkLoader.indexInfo(1);
};
window.ratingInfo = function () {
  return checkLoader.indexInfo(3);
};
window.labelInfo = function () {
  return checkLoader.indexInfo(2);
};

var polic = cookieOperate.getFromSessionCookie('_polic');
if (polic) {
  // 已经start了
  if (!window._polic) {
    startWithPolic(dispatch, polic);
  }
} else {
  checkLoader.init(dispatch);
  checkLoader.start(dispatch);
}
