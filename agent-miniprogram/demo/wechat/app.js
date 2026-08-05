// app.js
App({
  onLaunch: function (options) {
    //引用外部的probe.js文件并且初始化
    require('./probe.js');
    wx.init();
  }
})