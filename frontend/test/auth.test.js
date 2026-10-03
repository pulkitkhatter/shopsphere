import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decodeJwt, rolesOf, usernameOf, isExpired, createTokenStore } from '../public/js/auth.js';

const b64url = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
const jwt = (claims) => `${b64url({ alg: 'RS256' })}.${b64url(claims)}.signature`;

test('decodeJwt reads claims, including non-ASCII text', () => {
  const t = jwt({ sub: 'zoë', roles: ['USER'] });
  assert.equal(decodeJwt(t).sub, 'zoë');
});

test('decodeJwt returns null for garbage instead of throwing', () => {
  assert.equal(decodeJwt('not-a-token'), null);
  assert.equal(decodeJwt(''), null);
});

test('rolesOf / usernameOf', () => {
  const t = jwt({ sub: 'admin', roles: ['ADMIN', 'USER'] });
  assert.deepEqual(rolesOf(t), ['ADMIN', 'USER']);
  assert.equal(usernameOf(t), 'admin');
  assert.deepEqual(rolesOf(null), []);
  assert.deepEqual(rolesOf(jwt({ roles: 'ADMIN' })), []);      // malformed claim is ignored, never trusted
});

test('isExpired honours a safety skew', () => {
  const now = 1_000_000_000_000;
  const exp = now / 1000 + 5;                                 // expires in 5 s
  assert.equal(isExpired(jwt({ exp }), now, 0), false);
  assert.equal(isExpired(jwt({ exp }), now, 10), true);       // inside the 10 s skew counts as expired
  assert.equal(isExpired(jwt({}), now), true);
  assert.equal(isExpired(null, now), true);
});

test('token store keeps the access token in memory only and the refresh token in session storage', () => {
  const data = new Map();
  const storage = { getItem: (k) => data.get(k) ?? null, setItem: (k, v) => data.set(k, v), removeItem: (k) => data.delete(k) };
  const store = createTokenStore(storage);
  store.set({ access_token: 'AT', refresh_token: 'RT' });
  assert.equal(store.access, 'AT');
  assert.equal(store.refresh, 'RT');
  assert.deepEqual([...data.values()], ['RT']);               // the access token was never persisted
  store.clear();
  assert.equal(store.access, null);
  assert.equal(store.refresh, null);
});

test('token store survives unavailable storage', () => {
  const broken = { getItem() { throw new Error('x'); }, setItem() { throw new Error('x'); }, removeItem() { throw new Error('x'); } };
  const store = createTokenStore(broken);
  assert.doesNotThrow(() => store.set({ access_token: 'AT', refresh_token: 'RT' }));
  assert.equal(store.access, 'AT');
});
