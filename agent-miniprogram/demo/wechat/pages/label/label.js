Page({
  data: { high: [], medium: [], low: [], normal: [], other: [] },
  async onShow() {
    const app = getApp();
    try {
      await app.globalData.agentReady;
      const response = await app.globalData.agent.getLabelInfo();
      const values = response.data || [];
      const next = { high: [], medium: [], low: [], normal: [], other: [] };
      values.forEach((entry) => {
        if (next[entry.type]) next[entry.type] = entry.value_list || [];
      });
      this.setData(next);
    } catch (error) {
      console.warn('label query failed', error);
    }
  },
  showModal(event) {
    wx.showModal({ title: '提示', content: String(event.currentTarget.dataset.content || ''), showCancel: false });
  }
});
