Page({
  data: {
    deviceInfo: '加载中…',
    screenInfo: '',
    batteryInfo: '',
    otherInfo: ''
  },
  async onShow() {
    const app = getApp();
    try {
      await app.globalData.agentReady;
      const result = await app.globalData.agent.getDeviceInfo();
      const detail = result && result.data && (result.data.fact || result.data) || result;
      this.setData({
        deviceInfo: JSON.stringify(detail && detail.device || detail, null, 2),
        screenInfo: JSON.stringify(detail && detail.screen || {}, null, 2),
        batteryInfo: JSON.stringify(detail && detail.battery || {}, null, 2),
        otherInfo: JSON.stringify(detail && (detail.capabilities || detail.uuid) || {}, null, 2)
      });
    } catch (error) {
      this.setData({ deviceInfo: `查询失败：${error.message || error}` });
    }
  }
});
