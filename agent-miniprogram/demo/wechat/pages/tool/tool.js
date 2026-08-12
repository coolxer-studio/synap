function actionId() {
  return `demo-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

Page({
  data: { output: '不会获取头像、昵称或输入内容。' },
  agent() { return getApp().globalData.agent; },
  async ready() { return getApp().globalData.agentReady; },
  trackTap(event) {
    this.agent().trackInteraction(event, { name: 'demo-button' });
  },
  async sendOpaqueUser() {
    await this.ready();
    const result = this.agent().userData(`demo-user-${Math.random().toString(16).slice(2)}`, { scene: 'sdk-demo', level: 1 });
    this.setData({ output: `脱敏业务数据入队：${result}` });
  },
  async markLogin() {
    await this.ready();
    const id = actionId();
    this.agent().markAction({ id, name: 'login', phase: 'start' });
    this.agent().markAction({ id, name: 'login', phase: 'success' });
    this.setData({ output: `动作窗口已记录：${id}` });
  },
  triggerError() {
    setTimeout(() => { throw new Error('Synap Demo synthetic error token=[masked]'); }, 0);
  },
  async retryUpload() {
    await this.ready();
    try {
      const sent = await this.agent().flush();
      this.setData({ output: `主动 flush：${sent}` });
    } catch (error) {
      this.setData({ output: `离线/错误已进入重试：${error.type || error.message}` });
    }
  },
  simulateDebugPause() {
    const until = Date.now() + 2200;
    while (Date.now() < until) { /* test-only main-thread pause */ }
    this.setData({ output: '已制造约 2.2 秒前台长暂停，等待 DebugData 窗口。' });
  },
  showMetrics() {
    this.setData({ output: JSON.stringify(this.agent().getMetrics(), null, 2) });
  }
});
