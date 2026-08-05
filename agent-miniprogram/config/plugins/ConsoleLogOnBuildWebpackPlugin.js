const pluginName = 'ConsoleLogOnBuildWebpackPlugin';

class ConsoleLogOnBuildWebpackPlugin {
  apply(compiler) {
    compiler.hooks.run.tap(pluginName, compilation => {
      console.log('微信小程序监测探针SDK 构建开始！');
    });
    compiler.hooks.done.tap(pluginName, compilation => {
      console.log('微信小程序监测探针SDK 构建结束！');
    })
  }
}
module.exports = ConsoleLogOnBuildWebpackPlugin;
