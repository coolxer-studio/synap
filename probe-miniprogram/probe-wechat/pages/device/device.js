// index.js
Page({
  data: {
    deviceInfo: "",
    screenInfo: "",
    batteryInfo: "",
    otherInfo: ""
  },
  onLoad: function () {
    // 入口页面启动监测
    wx.start();
  },
  onShow: function () {
    // 查询设备信息并且显示
    let that = this;
    wx.deviceInfo(function (result) {
      var data = result;
      if (data.code === 0) {
        var deviceInfoData = {};
        const deviceInfoMap = {
          "deviceInfo": ["device"],
          "screenInfo": ["screen"],
          "batteryInfo": ["battery"],
          "otherInfo": ["uuid", "app_base_info", "system_setting", "app_authorize_setting"]
        };
        const detail = data.data.fact;
        for (const deviceInfoKey in deviceInfoMap) {
          if (Object.hasOwnProperty.call(deviceInfoMap, deviceInfoKey)) {
            const deviceInfoMapValueArray = deviceInfoMap[deviceInfoKey];
            var content = "";
            for (const detailKey of deviceInfoMapValueArray) {
              for (var contextKey in detail[detailKey]) {
                content += contextKey + ":" + detail[detailKey][contextKey] + "\n"
              }
            }
            deviceInfoData[deviceInfoKey] = content;
          }
        }
      }
      that.setData(deviceInfoData);
    });

  }

})