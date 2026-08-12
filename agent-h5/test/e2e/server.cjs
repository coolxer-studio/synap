const http = require('http');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../..');
const html = '<!doctype html><html><head><meta charset="utf-8"><script src="/e2e-config.js"></script><script src="/dist/probe.min.js"></script></head><body>SynapAgent E2E</body></html>';
const unconfiguredHtml = '<!doctype html><html><head><meta charset="utf-8"><script src="/dist/probe.min.js"></script></head><body>SynapAgent unconfigured E2E</body></html>';
const config = `window.SynapAgentConfig={appId:'synap-h5-e2e',baseUrl:'https://risk.test',serverTrustAnchors:{'e2e-key':'AA'}};`;

http.createServer((request, response) => {
  const pathname = new URL(request.url, 'http://127.0.0.1').pathname;
  if (pathname === '/unconfigured.html') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end(unconfiguredHtml);
    return;
  }
  if (pathname === '/' || pathname === '/csp.html') {
    if (pathname === '/csp.html') {
      response.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; connect-src https://risk.test");
    }
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end(html);
    return;
  }
  if (pathname === '/e2e-config.js') {
    response.setHeader('Content-Type', 'application/javascript');
    response.end(config);
    return;
  }
  const file = path.resolve(root, '.' + pathname);
  if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) {
    response.statusCode = 404;
    response.end('Not found');
    return;
  }
  response.setHeader('Content-Type', 'application/javascript');
  fs.createReadStream(file).pipe(response);
}).listen(4173, '127.0.0.1');
