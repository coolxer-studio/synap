import enc from './lib/secure-communication/encrypt-decrypt'
import baseData from './lib/base-data'
import ctDeviceInfo from './lib/device-info/ct-device-info'

// 检查
var startCheck = function (cancelTime) {
  if (wx.ctCheckStartTime) {
    // 超过检测周期不检测了
    var currentTime = new Date().getTime();
    if (currentTime - wx.ctCheckStartTime > cancelTime * 1000) {
      return;
    }
  } else {
    wx.ctCheckStartTime = new Date().getTime();
  }
  // 推送数据
  pushMsg(ctDeviceInfo.deviceInfo());
}

// 触发发送消息
var startSender = function (cancelTime) {
  if (wx.ctSendStartTime) {
    // 超过发送周期不发送了
    var currentTime = new Date().getTime();
    if (currentTime - wx.ctSendStartTime > cancelTime * 1000) {
      return;
    }
  } else {
    wx.ctSendStartTime = new Date().getTime();
  }
  // 开始发送
  const msgArray = wx.probeMsgQueue;
  if (msgArray.length === 0) {
    // 队列为空不需要发送数据
    return;
  }
  const body = {
    "common": baseData.getCommonInfo(),
    "data": msgArray
  };

  sendPostRequest(decodeString(wx.ctScArray[0]) + decodeString(wx.ctScArray[2]), body, function (result2) {
    wx.probeMsgQueue = [];
  });
};

// main

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

//  let array = ['https://demo.genie.coolxer.com:11098','/sc/h5/risk/start', '/sc/h5/risk/upload', '/sc/h5/device_index/info', '/sc/h5/device_index/rating', '/sc/h5/device_index/label_start'];
//  for (let i = 0; i < array.length; i++) {
//    var original = array[i];
//    var encoded = encodeString(original);
//    console.log("Encoded: ", encoded);
//    var decoded = decodeString(encoded);
//    console.log("Decoded: ", decoded);
//  }

function pushMsg(ep) {
  if (ep) {
    if (typeof wx.probeMsgQueue === 'undefined') {
      wx.probeMsgQueue = [];
    }
    if (wx.probeMsgQueue.length < 50) {
      wx.probeMsgQueue.push(ep);
    } else {
      console.warn('queue is to large:' + wx.probeMsgQueue.length);
    }
  }
}

// 发送POST请求
function sendPostRequest(url, data, callback) {
  const encBody = enc.Encrypt(JSON.stringify(data), enc.k(wx.ctKeyString)) + wx.ctKeyString.toUpperCase();
  wx.request({
    url: url,
    method: 'POST',
    data: encBody,
    header: {
      'Content-Type': 'application/json;charset=UTF-8',
      'code': baseData.getHeaderCode()
    },
    success: function (res) {
      var response = JSON.parse(enc.Decrypt(res.data, enc.k(wx.ctKeyString)));
      // console.log(JSON.stringify(response));
      callback(response);
    },
    fail: function (err) {
      console.error(err);
    }
  });
}

wx.deviceInfo = function (callback) {
  const body = {
    'common': baseData.getCommonInfo()
  };
  sendPostRequest(decodeString(wx.ctScArray[0]) + decodeString(wx.ctScArray[3]), body, function (result) {
    // 处理请求结果
    callback(result);
  });
};
wx.ratingInfo = function (callback) {
  const body = {
    'common': baseData.getCommonInfo()
  };
  sendPostRequest(decodeString(wx.ctScArray[0]) + decodeString(wx.ctScArray[4]), body, function (result) {
    // 处理请求结果
    callback(result);
  });
};
wx.labelInfo = function (callback) {
  const body = {
    'common': baseData.getCommonInfo()
  };
  sendPostRequest(decodeString(wx.ctScArray[0]) + decodeString(wx.ctScArray[5]), body, function (result) {
    // 处理请求结果
    callback(result);
  });
};
wx.init = function () {
  // 缓存网络类型
  wx.getNetworkType({
    success(res) {
      wx.ctNetworkType = res.networkType;
    }
  })

  // 缓存本地ip
  wx.getLocalIPAddress({
    success(res) {
      wx.ctLocalIp = res.localip;
    }
  })
  // 清除启动id
  wx.removeStorageSync('_s_d')
  // 配置初始值
  wx.ctKeyString = '2396641245c4fe6ttd041565b3651245'
  wx.ctScArray = ["iuuqt;00efnp/hfojf/dppmyfs/dpn;221:9", "0td0i60sjtl0tubsu", "0td0i60sjtl0vqmpbe", "0td0i60efwjdf`joefy0jogp", "0td0i60efwjdf`joefy0sbujoh", "0td0i60efwjdf`joefy0mbcfm`tubsu"]
  console.log('is init probe:true');

}

wx.start = function () {
  var startId = wx.getStorageSync('_s_d');
  if (!startId && !wx.ctPolic) {
    // 判断是否启动过，防止重复启动
    const body = {
      'common': baseData.getCommonInfo()
    };

    sendPostRequest(decodeString(wx.ctScArray[0]) + decodeString(wx.ctScArray[1]), body, function (result) {
      // 处理请求结果
      if (result && result.config.startConfig[0]) {
        wx.ctPolic = result;
        ctDeviceInfo.loadConfig(result.config)
        // 周期检测数据
        setInterval(startCheck, wx.ctPolic.config.startConfig[1] * 1000, wx.ctPolic.config.startConfig[2])

        // 回调数据
        // debug
        // ctRunningCheck.debugMonitor(pushMsg);
        // 周期发送数据
        setInterval(startSender, wx.ctPolic.config.startConfig[3] * 1000, wx.ctPolic.config.startConfig[4]);

        // 暴露用户数据接口
        wx.userData = function (userId, data, callback) {
          // userId存储
          wx.ctUserId = userId;
          var userDataMsg = {
            buildDate: baseData.currentFormatDate(),
            type: 'UserData',
            userId: userId,
            data: data
          }
          pushMsg(userDataMsg);
          callback(userDataMsg);
        }
      }
    });
  }
}