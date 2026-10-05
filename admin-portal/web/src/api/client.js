// The one HTTP client for the portal. JSON by default, Bearer when signed in.
// A 401 tries POST /api/auth/refresh once, retries the call, and otherwise
// clears the session so the router sends the admin back to login.

import { clearSession, getSession, sessionFromTokenResponse, setSession } from './session.js';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8010').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(status, body) {
    super((body && body.error) || `Request failed with status ${status}`);
    this.status = status;
    this.type = body?.type;
    this.code = body?.code;
    this.body = body;
  }
}

let refreshInFlight = null;

async function send(path, { method = 'GET', body, headers = {}, auth = true, signal } = {}) {
  const init = { method, headers: { Accept: 'application/json', ...headers }, signal };

  if (body instanceof FormData) {
    init.body = body; // the browser sets the multipart boundary
  } else if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  const token = auth ? getSession()?.accessToken : null;
  if (token) init.headers.Authorization = `Bearer ${token}`;

  return fetch(`${API_BASE_URL}${path}`, init);
}

async function parse(res) {
  if (res.status === 204) return null;
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/** Trades the stored refresh token for new tokens. Concurrent 401s share one refresh. */
function refreshSession() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = getSession()?.refreshToken;
      if (!refreshToken) return false;
      try {
        const res = await send('/api/auth/refresh', {
          method: 'POST',
          body: { refresh_token: refreshToken },
          auth: false,
        });
        if (!res.ok) return false;
        const data = await res.json();
        if (data.role !== 'ADMIN') return false;
        setSession(sessionFromTokenResponse(data));
        return true;
      } catch {
        return false;
      }
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

/** Sends a request with the session's Bearer token; on 401, refreshes once and retries. */
async function request(path, options = {}) {
  let res = await send(path, options);

  if (res.status === 401 && options.auth !== false && getSession()) {
    if (await refreshSession()) {
      res = await send(path, options);
    }
    if (res.status === 401) {
      clearSession();
    }
  }
  return res;
}

/**
 * Calls the API and returns the parsed body, or throws ApiError.
 * Options: method, body (object → JSON, FormData → multipart), headers, auth (default true), signal.
 */
export async function api(path, options = {}) {
  const res = await request(path, options);
  const data = await parse(res);
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

/**
 * Fetches a file through the same client (so the Bearer header is sent) and saves it.
 * Throws ApiError with the server's message on failure; never saves an empty file.
 */
export async function download(path, filename, options = {}) {
  const res = await request(path, { ...options, headers: { Accept: '*/*', ...options.headers } });
  if (!res.ok) throw new ApiError(res.status, await parse(res));

  const blob = await res.blob();
  if (blob.size === 0) throw new ApiError(res.status, { error: 'The server returned an empty file.' });

  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
  return blob;
}
