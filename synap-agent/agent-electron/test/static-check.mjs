import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const preload = fs.readFileSync(path.join(root, 'src/preload-runtime.js'), 'utf8');
const main = fs.readFileSync(path.join(root, 'src/main-runtime.js'), 'utf8');
const renderer = fs.readFileSync(path.join(root, 'src/renderer-runtime.js'), 'utf8');

assert.equal(/exposeInMainWorld\([^,]+,\s*ipcRenderer/.test(preload), false, 'preload must not expose ipcRenderer');
assert.equal(/process\.env|process\.argv|os\.homedir|os\.hostname|getPath\(/.test(main), false, 'main snapshot must not collect paths or environment data');
assert.match(renderer, /protocol === 'file:'/);
assert.match(renderer, /hostContext/);
console.log('Electron Agent static checks passed');
