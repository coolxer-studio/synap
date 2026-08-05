// pages/rating/rating.js
Page({

  /**
   * 页面的初始数据
   */
  data: {
    textColor: "#20C997",
    grade: "加载中",
    score: 0
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
    // 获取评级指数并且显示
    let that = this;
    wx.ratingInfo(function (result) {
      if (result.code === 0) {
        var currentTextColor = "black";
        if (result.data.grade === "低风险") {
          currentTextColor = "#20C997"
        } else if (result.data.grade === "中风险") {
          currentTextColor = "#FCC419"
        } else if (result.data.grade === "高风险") {
          currentTextColor = "#FA5252"
        }
        that.setData({
          textColor: currentTextColor,
          grade: result.data.grade,
          score: result.data.score
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

  }
})