export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.error_description || problem?.title || `Request failed (${status})`);
    this.status = status;
    this.problem = problem;
    this.fieldErrors = problem?.errors || {};
  }
}

/**
 * Small fetch wrapper:
 *  - adds the bearer token,
 *  - on 401 refreshes the access token ONCE (concurrent requests share one refresh call, because refresh tokens
 *    are single-use and a second parallel refresh would fail and log the user out) and retries,
 *  - turns RFC 7807 / OAuth error bodies into ApiError.
 */
export function createApi({ baseUrl, tokens, fetchImpl = globalThis.fetch.bind(globalThis), onSignedOut = () => {} }) {
  let refreshing = null;

  async function refresh() {
    const token = tokens.refresh;
    if (!token) throw new ApiError(401, { title: 'Not signed in' });
    refreshing ??= fetchImpl(`${baseUrl}/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: token }),
    }).then(async (res) => {
      if (!res.ok) { tokens.clear(); onSignedOut(); throw new ApiError(res.status, await safeJson(res)); }
      tokens.set(await res.json());
    }).finally(() => { refreshing = null; });
    return refreshing;
  }

  async function request(method, path, { body, headers = {}, auth = true, retry = true } = {}) {
    const h = { Accept: 'application/json', ...headers };
    if (body !== undefined) h['Content-Type'] = 'application/json';
    if (auth && tokens.access) h.Authorization = `Bearer ${tokens.access}`;
    const res = await fetchImpl(`${baseUrl}${path}`, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });

    if (res.status === 401 && auth && retry && tokens.refresh) {
      await refresh();
      return request(method, path, { body, headers, auth, retry: false });
    }
    if (!res.ok) throw new ApiError(res.status, await safeJson(res));
    return { status: res.status, headers: res.headers, data: res.status === 204 ? null : await safeJson(res) };
  }

  async function form(path, fields) {
    const res = await fetchImpl(`${baseUrl}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(fields),
    });
    if (!res.ok) throw new ApiError(res.status, await safeJson(res));
    return res.json();
  }

  return {
    request,
    get: (p, o) => request('GET', p, o),
    post: (p, body, o) => request('POST', p, { ...o, body }),
    put: (p, body, o) => request('PUT', p, { ...o, body }),
    del: (p, o) => request('DELETE', p, o),
    login: async (username, password) => tokens.set(await form('/oauth/token', { grant_type: 'password', username, password })),
    register: (username, email, password) => request('POST', '/auth/register', { body: { username, email, password }, auth: false }),
    logout: () => tokens.clear(),
  };
}

async function safeJson(res) {
  try { return await res.json(); } catch { return null; }
}
