import { createContext, useContext, useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { clearSession, getSession, sessionFromTokenResponse, setSession, subscribe } from '../api/session.js';

const AuthContext = createContext(null);

export class AccessDeniedError extends Error {
  constructor(role) {
    super('This account is not an administrator.');
    this.role = role;
  }
}

export function AuthProvider({ children }) {
  const [session, setState] = useState(getSession);

  useEffect(() => subscribe(setState), []);

  async function login(email, password) {
    const res = await api('/api/auth/login', {
      method: 'POST',
      body: { email, password },
      auth: false,
    });

    if (res.role !== 'ADMIN') {
      // Store nothing, and end the Keycloak session the login just opened.
      api('/api/auth/logout', {
        method: 'POST',
        body: { refresh_token: res.refresh_token },
        auth: false,
      }).catch(() => {});
      throw new AccessDeniedError(res.role);
    }

    setSession(sessionFromTokenResponse(res));
  }

  async function logout() {
    const refreshToken = getSession()?.refreshToken;
    try {
      if (refreshToken) {
        await api('/api/auth/logout', {
          method: 'POST',
          body: { refresh_token: refreshToken },
          auth: false,
        });
      }
    } catch {
      // The local session ends either way.
    } finally {
      clearSession();
    }
  }

  return <AuthContext.Provider value={{ session, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
