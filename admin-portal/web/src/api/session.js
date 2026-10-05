// The signed-in admin: tokens plus the account fields from the login response.
// Kept in memory and mirrored to sessionStorage so a reload keeps the session
// but closing the tab ends it.

const STORAGE_KEY = 'tms.admin.session';

let current = read();
const listeners = new Set();

function read() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function getSession() {
  return current;
}

export function setSession(session) {
  current = session;
  try {
    if (session) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Storage blocked: the in-memory copy still works for this page load.
  }
  listeners.forEach((fn) => fn(current));
}

export function clearSession() {
  setSession(null);
}

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** Builds the stored session from a /api/auth/login or /api/auth/refresh response. */
export function sessionFromTokenResponse(res) {
  return {
    accessToken: res.access_token,
    refreshToken: res.refresh_token,
    email: res.email,
    name: res.name,
    role: res.role,
  };
}
