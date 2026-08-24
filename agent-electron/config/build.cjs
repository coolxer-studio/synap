const fs = require('node:fs');
const path = require('node:path');

let webpack;
try {
  webpack = require('webpack');
} catch (_) {
  webpack = require('../../agent-h5/node_modules/webpack');
}

const root = path.resolve(__dirname, '..');
const outputPath = path.join(root, 'dist');

function config(name, entry, target, moduleOutput) {
  const electronExternal = moduleOutput ? 'module electron' : 'commonjs electron';
  return {
    name,
    mode: 'production',
    target,
    entry: path.join(root, entry),
    devtool: false,
    output: {
      path: outputPath,
      filename: moduleOutput ? `${name}.js` : `${name}.cjs`,
      library: { type: moduleOutput ? 'module' : 'commonjs2' },
      module: moduleOutput,
      clean: false
    },
    experiments: { outputModule: moduleOutput },
    externalsType: moduleOutput ? 'module' : 'commonjs',
    externals: target === 'web' ? {} : { electron: electronExternal },
    optimization: { minimize: name === 'renderer' },
    performance: { hints: false },
    resolve: { extensions: ['.js'] }
  };
}

fs.rmSync(outputPath, { recursive: true, force: true });
fs.mkdirSync(outputPath, { recursive: true });

const configs = [
  config('main', 'src/main.js', 'electron-main', false),
  config('main', 'src/main.js', 'electron-main', true),
  config('renderer', 'src/renderer.js', 'web', false),
  config('renderer', 'src/renderer.js', 'web', true),
  config('preload', 'src/preload.js', 'electron-preload', false)
];

webpack(configs, (error, stats) => {
  if (error) throw error;
  const output = stats.toString({ colors: true, modules: false, chunks: false, children: true });
  process.stdout.write(`${output}\n`);
  if (stats.hasErrors()) process.exitCode = 1;
  if (stats.hasErrors()) return;
  for (const name of ['main.d.ts', 'preload.d.ts', 'renderer.d.ts']) {
    fs.copyFileSync(path.join(root, 'types', name), path.join(outputPath, name));
  }
});
