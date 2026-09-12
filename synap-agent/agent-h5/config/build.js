const chalk = require('chalk')
const webpack = require('webpack')
const webpackConfig = require('./webpack.config.js')
const ora = require('ora');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');


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
    if (stats.hasErrors()) {
        process.exitCode = 1
        return
    }
    const bm = process.env.NODE_ENV === 'production' ? 'min' : 'dev';
    const outputFile = path.resolve(__dirname, `../dist/probe.${bm}.js`);
    if (fs.existsSync(outputFile)) {
        const sri = `sha384-${crypto.createHash('sha384').update(fs.readFileSync(outputFile)).digest('base64')}`;
        fs.writeFileSync(`${outputFile}.sha384`, `${sri}\n`, 'utf8');
        const demoFile = path.resolve(__dirname, '../demo/js/probe.js');
        fs.copyFileSync(outputFile, demoFile);
        console.log(chalk.green(`SRI: ${sri}`));
    }
    console.log(chalk.green(`\n编译完成:)`));
    console.log(chalk.green(
        '\n文件输出目录: \n' +
        `dist/probe.${bm}.js\n`
    ))

})
