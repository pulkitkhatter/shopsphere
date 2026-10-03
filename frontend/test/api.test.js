import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApi, ApiError } from '../public/js/api.js';
import { createTokenStore } from '../public/js/auth.js';

const json = (status, body, headers = {}) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });
const memoryStorage = () => { const m = new Map(); return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v), removeItem: (k) => m.delete(k) }; };

function setup(handler) {
  const calls = [];
  const fetchImpl = async (url, init) => { calls.push({ url, init }); return handler(url, init, calls); };
  const tokens = createTokenStore(memoryStorage());
  let signedOut = 0;
  const api = createApi({ baseUrl: 'http://gw', tokens, fetchImpl, onSignedOut: () => signedOut++ });
  return { api, tokens, calls, signedOut: () => signedOut };
}

test('sends the bearer token and parses JSON', async () => {
  const { api, tokens, calls } = setup(() => json(200, { ok: true }));
  tokens.set({ access_token: 'AT' });
  const res = await api.get('/api/v1/orders');
  assert.deepEqual(res.data, { ok: true });
  assert.equal(calls[0].init.headers.Authorization, 'Bearer AT');
});

test('public calls (auth:false) never send the token', async () => {
  const { api, tokens, calls } = setup(() => json(200, {}));
  tokens.set({ access_token: 'AT' });
  await api.get('/api/v2/products', { auth: false });
  assert.equal(calls[0].init.headers.Authorization, undefined);
});

test('RFC 7807 problems become ApiError with detail and field errors', async () => {
  const { api } = setup(() => json(400, { title: 'Validation failed', detail: 'Request body is invalid', errors: { sku: 'bad' } }));
  await assert.rejects(api.post('/api/v2/products', {}), (e) => {
    assert.ok(e instanceof ApiError);
    assert.equal(e.status, 400);
    assert.equal(e.message, 'Request body is invalid');
    assert.deepEqual(e.fieldErrors, { sku: 'bad' });
    return true;
  });
});

test('OAuth error bodies are understood too', async () => {
  const { api } = setup(() => json(400, { error: 'invalid_grant', error_description: 'Bad credentials' }));
  await assert.rejects(api.login('bob', 'wrong'), /Bad credentials/);
});

test('401 triggers one refresh, then the request is retried with the new token', async () => {
  const { api, tokens, calls } = setup((url, init) => {
    if (url.endsWith('/oauth/token')) return json(200, { access_token: 'AT2', refresh_token: 'RT2' });
    return init.headers.Authorization === 'Bearer AT2' ? json(200, { fine: true }) : json(401, {});
  });
  tokens.set({ access_token: 'AT1', refresh_token: 'RT1' });

  const res = await api.get('/api/v1/orders');

  assert.deepEqual(res.data, { fine: true });
  assert.equal(tokens.refresh, 'RT2');
  assert.equal(calls.filter((c) => c.url.endsWith('/oauth/token')).length, 1);
  assert.match(String(calls.find((c) => c.url.endsWith('/oauth/token')).init.body), /grant_type=refresh_token/);
});

test('concurrent 401s share ONE refresh call (refresh tokens are single use)', async () => {
  let refreshCalls = 0;
  const { api, tokens } = setup(async (url, init) => {
    if (url.endsWith('/oauth/token')) { refreshCalls++; await new Promise((r) => setTimeout(r, 20)); return json(200, { access_token: 'AT2', refresh_token: 'RT2' }); }
    return init.headers.Authorization === 'Bearer AT2' ? json(200, { n: 1 }) : json(401, {});
  });
  tokens.set({ access_token: 'AT1', refresh_token: 'RT1' });

  const results = await Promise.all([api.get('/a'), api.get('/b'), api.get('/c')]);

  assert.equal(results.length, 3);
  assert.equal(refreshCalls, 1);
});

test('failed refresh signs the user out and surfaces the error', async () => {
  const { api, tokens, signedOut } = setup((url) => url.endsWith('/oauth/token') ? json(400, { error: 'invalid_grant' }) : json(401, {}));
  tokens.set({ access_token: 'AT1', refresh_token: 'RT1' });

  await assert.rejects(api.get('/api/v1/orders'), ApiError);

  assert.equal(tokens.access, null);
  assert.equal(tokens.refresh, null);
  assert.equal(signedOut(), 1);
});

test('a request is retried at most once after refreshing (no infinite loop on a persistent 401)', async () => {
  const { api, tokens, calls } = setup((url) => url.endsWith('/oauth/token') ? json(200, { access_token: 'AT2', refresh_token: 'RT2' }) : json(401, {}));
  tokens.set({ access_token: 'AT1', refresh_token: 'RT1' });

  await assert.rejects(api.get('/api/v1/orders'), (e) => e.status === 401);

  assert.equal(calls.filter((c) => !c.url.endsWith('/oauth/token')).length, 2);
});

test('204 responses have no body', async () => {
  const { api, tokens } = setup(() => new Response(null, { status: 204 }));
  tokens.set({ access_token: 'AT' });
  assert.equal((await api.del('/api/v2/products/1')).data, null);
});

test('login stores both tokens; logout clears them', async () => {
  const { api, tokens } = setup(() => json(200, { access_token: 'AT', refresh_token: 'RT' }));
  await api.login('alice', 'pw');
  assert.equal(tokens.access, 'AT');
  api.logout();
  assert.equal(tokens.access, null);
});
