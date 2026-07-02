const fs = require('fs');
const path = require('path');
const webpack = require('webpack');
const chalk = require('chalk')
// const CopyPlugin = require("copy-webpack-plugin");
// const HtmlWebpackPlugin = require('html-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const CleanWebpackPlugin = require('clean-webpack-plugin');
const ConsoleLogOnBuildWebpackPlugin = require('./plugins/ConsoleLogOnBuildWebpackPlugin');

// const assetsSubDirectory = 'static';

const handler = (percentage, message, ...args) => {
  if (percentage === 0) {
    console.log(chalk.cyan('***                                               '));
    console.log(chalk.cyan('******************                                '));
    console.log(chalk.cyan('*********************************                 '));
    console.log(chalk.cyan('**************************************************'));
    console.log(chalk.green(`\n正在编译synap-agent-h5探针：${process.env.NODE_ENV}`));
  }
  if (percentage === 1) {
    // console.log(chalk.green(`\n编译完成:)`));
    if (process.env.NODE_ENV === 'production') {
      console.log(chalk.green(
          '\n文件输出目录: \n' +
          'dist/probe.*.js\n'
      ))
    } else {
      const d = new Date();
      console.log(chalk.green(`\n[${[d.getFullYear(),d.getUTCMonth() + 1,d.getDate()].join('-')} ${[d.getHours(),d.getMinutes(),d.getSeconds(),d.getMilliseconds()].join(':')}] 编译完成:)\n`));

      if (fs.existsSync(path.resolve(__dirname, '../demo/js/probe.js'))) fs.unlinkSync(path.resolve(__dirname, '../demo/js/probe.js'));
      if (fs.existsSync(path.resolve(__dirname, '../demo/js/probe.js.map'))) fs.unlinkSync(path.resolve(__dirname, '../demo/js/probe.js.map'));
      fs.copyFileSync(path.resolve(__dirname, '../dist/probe.dev.js'), path.resolve(__dirname, '../demo/js/probe.js'));
      fs.copyFileSync(path.resolve(__dirname, '../dist/probe.dev.js.map'), path.resolve(__dirname, '../demo/js/probe.js.map'));
      console.log(chalk.yellow(
          '  本地开发模式可打开demo目录下的文件进行测试.\n' +
          '  也可以自行便携式demo文件集成dist目录下的probe.*.js文件.\n'
      ));
      console.log(chalk.blue(
          '  本地文件正在监听中...\n'
      ))
    }

  }
};

module.exports = {
  devtool: process.env.NODE_ENV === 'production' ? false : 'source-map',
  mode: process.env.NODE_ENV,
  resolve: {
    alias: {
      "crypto": false,
      "crypto-browserify": require.resolve('crypto-browserify'), //if you want to use this module also don't forget npm i crypto-browserify
    }
  },
  entry: {
    probe: './src/index.js'
  },
  target: "web",
  output: {
    filename: process.env.NODE_ENV === 'production' ? '[name].min.js' : '[name].dev.js',
    path: path.resolve(__dirname, '../dist'),
    environment: {
      arrowFunction: false,
      const: false,
    }
  },
  optimization: {
    minimize: process.env.NODE_ENV === 'production',
    minimizer: [new TerserPlugin({
      parallel: true,
      terserOptions: {
        ecma: undefined,
        parse: {},
        compress: {},
        mangle: true, // Note `mangle.properties` is `false` by default.
        module: false,
        // Deprecated
        output: null,
        format: null,
        toplevel: false,
        nameCache: null,
        ie8: true,
        keep_classnames: undefined,
        keep_fnames: /constructor$/i,
        safari10: true,
      },
    })],
  },
  module: {
    rules:[
      {
        test: /\.js$/,
        loader: 'babel-loader',
        exclude: /node_modules/
      }
    ]
  },
  plugins: [
    new webpack.ProgressPlugin(handler),
    new ConsoleLogOnBuildWebpackPlugin(),
    new CleanWebpackPlugin.CleanWebpackPlugin(),
  ],
};
