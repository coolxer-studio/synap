const http = require('http');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../..');
const html = '<!doctype html><html><head><meta charset="utf-8"><script src="/e2e-config.js"></script><script src="/dist/probe.min.js"></script></head><body>SynapAgent E2E</body></html>';
const config = `window.SynapAgentConfig={appKey:'1596861234c4ea6ddd041d45b3912345',baseUrl:'http://risk.test',collectionProfile:'minimal'};`;

http.createServer((request, response) => {
  if (request.url === '/' || request.url === '/csp.html') {
    if (request.url === '/csp.html') {
      response.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; connect-src http://risk.test");
    }
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end(html);
    return;
  }
  if (request.url === '/e2e-config.js') {
    response.setHeader('Content-Type', 'application/javascript');
    response.end(config);
    return;
  }
  const file = path.resolve(root, '.' + request.url);
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) {
    response.statusCode = 404;
    response.end('Not found');
    return;
  }
  response.setHeader('Content-Type', 'application/javascript');
  fs.createReadStream(file).pipe(response);
}).listen(4173, '127.0.0.1');

