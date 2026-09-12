const path = require('path');
const TerserPlugin = require('terser-webpack-plugin');

module.exports = function webpackConfig(_env, argv) {
  const production = argv.mode === 'production';
  return {
    mode: production ? 'production' : 'development',
    target: ['web', 'es5'],
    entry: path.resolve(__dirname, '../src/index.js'),
    devtool: production ? false : 'source-map',
    output: {
      path: path.resolve(__dirname, '../dist'),
      filename: production ? 'agent.min.js' : 'agent.js',
      library: { type: 'commonjs2' },
      clean: true,
      environment: {
        arrowFunction: false,
        const: false,
        destructuring: false
      }
    },
    module: {
      rules: [{
        test: /\.js$/,
        exclude: /node_modules/,
        use: {
          loader: 'babel-loader',
          options: {
            presets: [['@babel/preset-env', { bugfixes: true, modules: false }]]
          }
        }
      }]
    },
    optimization: {
      minimize: production,
      minimizer: [new TerserPlugin({
        extractComments: false,
        terserOptions: {
          ecma: 5,
          compress: { passes: 2 },
          mangle: true,
          format: { comments: false }
        }
      })]
    },
    performance: { hints: false }
  };
};
