import { createContext, useEffect, useState, type ReactNode } from 'react';
import { clearAccessToken, setAccessToken } from '../api/accessToken';
import {
  login as loginRequest,
  logout as logoutRequest,
  refreshSession,
  register as registerRequest,
} from '../api/client';
import type { User } from '../api/types';

/**
 * Vite only exposes env values as strings, so we compare with 'true'.
 * When this is false the whole login feature is switched off: no header links, no routes,
 * and no call to /auth/refresh.
 */
export const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED === 'true';

/**
 * Keys from earlier versions, when tokens were kept in localStorage. Tokens don't live there
 * anymore, so we delete any that are left over. A forgotten token in localStorage is still a
 * secret an XSS bug could steal.
 */
const OLD_TOKEN_KEYS = ['courseFinder.token', 'courseFinder.accessToken', 'courseFinder.refreshToken'];

export interface AuthContextValue {
  user: User | null;
  /** True while the silent /auth/refresh on app start hasn't answered yet. */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  // The refresh cookie is httpOnly, so JavaScript can't check whether it exists. The only way
  // to find out if someone is logged in is to ask the backend, so with auth on we always
  // start in "loading".
  const [loading, setLoading] = useState(AUTH_ENABLED);

  useEffect(() => {
    // Delete old tokens even with the flag off: there's no reason to keep secrets lying around.
    OLD_TOKEN_KEYS.forEach((key) => localStorage.removeItem(key));

    if (!AUTH_ENABLED) return;

    // AbortController lets us cancel the request if this effect is cleaned up
    // (React StrictMode runs effects twice in development).
    const controller = new AbortController();

    // Silent check: the browser sends the cookie if there is one.
    refreshSession(controller.signal)
      .then((session) => {
        setAccessToken(session.accessToken);
        setUser(session.user);
        setLoading(false);
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        // 401 just means "no session", the normal case for a guest. It isn't an error, so we
        // show nothing. Same if the server can't be reached: we treat the visitor as a guest.
        clearAccessToken();
        setUser(null);
        setLoading(false);
      });

    return () => controller.abort();
  }, []);

  async function login(email: string, password: string) {
    // The backend also sets the refresh cookie on this response; the browser stores it for us.
    const response = await loginRequest(email, password);
    setAccessToken(response.accessToken);
    setUser(response.user);
  }

  async function register(email: string, password: string, displayName: string) {
    const response = await registerRequest(email, password, displayName);
    setAccessToken(response.accessToken);
    setUser(response.user);
  }

  async function logout() {
    try {
      // Only the backend can clear an httpOnly cookie, so this call is the real sign-out.
      await logoutRequest();
    } catch {
      // Server down: not a reason to keep the user signed in on this page.
    } finally {
      // Always forget the user locally, whatever the server said.
      clearAccessToken();
      setUser(null);
    }
  }

  return (
    // Children render straight away, no matter what `loading` is. That's what keeps
    // the search page from ever waiting on auth.
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>{children}</AuthContext.Provider>
  );
}
