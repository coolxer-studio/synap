const fs = require('fs');
const zlib = require('zlib');

const file = process.argv[2];
const limit = 65 * 1024;
if (!file || !fs.existsSync(file)) {
  console.error(`Bundle not found: ${file || '(missing path)'}`);
  process.exit(1);
}
const source = fs.readFileSync(file);
const gzipBytes = zlib.gzipSync(source).length;
console.log(`bundle=${source.length} bytes, gzip=${gzipBytes} bytes, limit=${limit} bytes`);
if (gzipBytes > limit) process.exit(1);

