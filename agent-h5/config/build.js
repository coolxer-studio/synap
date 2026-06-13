const exec = require('child_process').execSync

const fs = require('fs');
const path = require('path')
const chalk = require('chalk')
const webpack = require('webpack')
const webpackConfig = require('./webpack.config.js')
const ora = require('ora');


var spinner = ora('building for production...\n')


spinner.start()

webpack(webpackConfig, function (err, stats) {
    spinner.stop()
    if (err) throw err
    process.stdout.write(stats.toString({
        colors: true,
        modules: false,
        children: false,
        chunks: false,
        chunkModules: false
    }) + '\n')
    const bm = process.env.NODE_ENV === 'production' ? 'min' : 'dev';
    console.log(chalk.green(`\n编译完成:)`));
    console.log(chalk.green(
        '\n文件输出目录: \n' +
        `dist/probe.${bm}.js\n` +
        `dist/core.${bm}.js\n`
    ))

})
