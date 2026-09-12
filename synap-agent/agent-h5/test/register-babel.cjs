const fs = require('fs');
const Module = require('module');
const path = require('path');
const babel = require('@babel/core');

const sourceRoot = path.resolve(__dirname, '../src');
const originalLoader = Module._extensions['.js'];

Module._extensions['.js'] = function transpile(module, filename) {
  if (!filename.startsWith(sourceRoot + path.sep)) {
    return originalLoader(module, filename);
  }
  const source = fs.readFileSync(filename, 'utf8');
  const result = babel.transformSync(source, {
    filename,
    babelrc: false,
    configFile: false,
    presets: [[require('@babel/preset-env'), { targets: { node: 'current' }, modules: 'commonjs' }]]
  });
  module._compile(result.code, filename);
};

