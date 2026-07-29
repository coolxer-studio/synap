// pages/label/label.js
Page({

  /**
   * 页面的初始数据
   */
  data: {
    high: [],
    medium: [],
    low: [],
    normal: [],
    other: []
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {

  },

  /**
   * 生命周期函数--监听页面初次渲染完成
   */
  onReady() {

  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {
    // 查询标签并且显示
    let that = this;
    var highArray = [];
    var mediumArray = [];
    var lowArray = [];
    var normalArray = [];
    var otherArray = [];
    wx.labelInfo(function (result) {
      if (result.code === 0) {
        for (let i = 0; i < result.data.length; i++) {
          if (result.data[i].type === "high") {
            highArray = result.data[i].value_list;
          } else if (result.data[i].type === "medium") {
            mediumArray = result.data[i].value_list;
          } else if (result.data[i].type === "low") {
            lowArray = result.data[i].value_list;
          } else if (result.data[i].type === "normal") {
            normalArray = result.data[i].value_list;
          } else if (result.data[i].type === "other") {
            otherArray = result.data[i].value_list;
          }
        }
        that.setData({
          high: highArray,
          medium: mediumArray,
          low: lowArray,
          normal: normalArray,
          other: otherArray
        });
      }
    });
  },

  /**
   * 生命周期函数--监听页面隐藏
   */
  onHide() {

  },

  /**
   * 生命周期函数--监听页面卸载
   */
  onUnload() {

  },

  /**
   * 页面相关事件处理函数--监听用户下拉动作
   */
  onPullDownRefresh() {

  },

  /**
   * 页面上拉触底事件的处理函数
   */
  onReachBottom() {

  },

  /**
   * 用户点击右上角分享
   */
  onShareAppMessage() {

  },
  showModal: function (e) {
    const dd = e.currentTarget.dataset.content;
    console.log(dd);
    wx.showModal({
      title: '提示',
      content: e.currentTarget.dataset.content,
      showCancel: false,
      confirmText: '确定',
      success: function (res) {
        if (res.confirm) {
          console.log('用户点击确定');
          // 用户点击确定时的逻辑处理
        }
      }
    });
  }
})