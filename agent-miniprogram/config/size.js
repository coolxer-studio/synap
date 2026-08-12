const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const file = path.resolve(__dirname, '../dist/agent.min.js');
const source = fs.readFileSync(file);
const gzip = zlib.gzipSync(source);
const rawLimit = 150 * 1024;
const gzipLimit = 40 * 1024;
process.stdout.write(`agent.min.js: ${source.length} bytes, gzip: ${gzip.length} bytes\n`);
if (source.length > rawLimit || gzip.length > gzipLimit) {
  throw new Error(`Bundle exceeds budget (${rawLimit} raw / ${gzipLimit} gzip)`);
}
