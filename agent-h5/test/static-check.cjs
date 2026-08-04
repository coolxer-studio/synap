const fs = require('fs');
const path = require('path');
const babel = require('@babel/core');

const root = path.resolve(__dirname, '../src');
const files = [];
function visit(directory) {
  fs.readdirSync(directory, { withFileTypes: true }).forEach((entry) => {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) visit(file);
    else if (entry.name.endsWith('.js')) files.push(file);
  });
}
visit(root);

files.forEach((file) => {
  const source = fs.readFileSync(file, 'utf8');
  babel.transformSync(source, { filename: file, ast: false, code: false });
});

const debugSource = fs.readFileSync(path.join(root, 'lib/running-check/debug.js'), 'utf8');
if (/new\s+Function|\.constructor\s*\(\s*["']debugger/.test(debugSource)) {
  throw new Error('Debug monitor must remain compatible with strict CSP');
}
const eventSource = fs.readFileSync(path.join(root, 'lib/user-behavior/event-listener.js'), 'utf8');
if (/buildTime\s*:/.test(eventSource) || !/buildDate\s*:/.test(eventSource)) {
  throw new Error('EventData must use the server-compatible buildDate field');
}
console.log(`Checked ${files.length} source files`);
