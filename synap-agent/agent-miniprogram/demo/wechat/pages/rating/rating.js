Page({
  data: { textColor: '#20C997', grade: '加载中', score: 0 },
  async onShow() {
    const app = getApp();
    try {
      await app.globalData.agentReady;
      const response = await app.globalData.agent.getRatingInfo();
      const data = response.data || response;
      const colors = { '低风险': '#20C997', '中风险': '#FCC419', '高风险': '#FA5252' };
      this.setData({ textColor: colors[data.grade] || 'black', grade: data.grade || '未知', score: data.score || 0 });
    } catch (error) {
      this.setData({ grade: `查询失败：${error.message || error}` });
    }
  }
});
