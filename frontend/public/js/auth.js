// Token handling. The access token lives only in memory (not reachable by other tabs or persisted XSS loot);
// the refresh token is kept in sessionStorage so a reload keeps you signed in, but closing the tab signs you out.
const REFRESH_KEY = 'shopsphere.refresh';

export function decodeJwt(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(atob(payload).split('').map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join(''));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/** For UI decisions only (show/hide the Admin menu). Authorisation is always enforced by the servers. */
export function rolesOf(token) {
  const claims = token ? decodeJwt(token) : null;
  return Array.isArray(claims?.roles) ? claims.roles : [];
}

export function usernameOf(token) {
  return (token && decodeJwt(token)?.sub) || null;
}

export function isExpired(token, nowMs = Date.now(), skewSeconds = 10) {
  const exp = token ? decodeJwt(token)?.exp : null;
  return !exp || exp * 1000 - skewSeconds * 1000 <= nowMs;
}

export function createTokenStore(storage = globalThis.sessionStorage) {
  let access = null;
  const safe = (fn) => { try { return fn(); } catch { return null; } };
  return {
    get access() { return access; },
    get refresh() { return safe(() => storage.getItem(REFRESH_KEY)); },
    set({ access_token, refresh_token }) {
      access = access_token ?? null;
      if (refresh_token) safe(() => storage.setItem(REFRESH_KEY, refresh_token));
    },
    clear() {
      access = null;
      safe(() => storage.removeItem(REFRESH_KEY));
    },
  };
}
