App({
  globalData: {
    agent: null,
    agentReady: null
  },
  onLaunch() {
    console.warn('Synap security protocol v1 does not support WeChat Mini Program');
  }
});
