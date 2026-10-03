import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { server, resolveSafe, SECURITY_HEADERS } from '../server.js';

test('resolveSafe maps / to index.html and blocks path traversal', () => {
  assert.match(resolveSafe('/'), /public\/index\.html$/);
  assert.match(resolveSafe('/js/app.js?x=1'), /public\/js\/app\.js$/);
  assert.equal(resolveSafe('/../server.js'), null);
  assert.equal(resolveSafe('/%2e%2e/server.js'), null);
  assert.equal(resolveSafe('/js/../../package.json'), null);
});

test('CSP forbids inline script and framing', () => {
  const csp = SECURITY_HEADERS['Content-Security-Policy'];
  assert.match(csp, /script-src 'self'/);
  assert.doesNotMatch(csp, /unsafe-inline/);
  assert.match(csp, /frame-ancestors 'none'/);
});

let port;
await new Promise((resolve) => server.listen(0, resolve));
port = server.address().port;
after(() => server.close());

test('serves the app with security headers; rejects writes; 404 for unknown files', async () => {
  const home = await fetch(`http://localhost:${port}/`);
  assert.equal(home.status, 200);
  assert.equal(home.headers.get('x-content-type-options'), 'nosniff');
  assert.equal(home.headers.get('x-frame-options'), 'DENY');
  assert.match(await home.text(), /ShopSphere/);

  assert.equal((await fetch(`http://localhost:${port}/missing.js`)).status, 404);
  assert.equal((await fetch(`http://localhost:${port}/`, { method: 'POST' })).status, 405);
  const traversal = await fetch(`http://localhost:${port}/..%2f..%2fetc/passwd`);
  assert.ok([400, 404].includes(traversal.status));
});
