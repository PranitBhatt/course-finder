import { createContext, useEffect, useState, type ReactNode } from 'react';
import {
  fetchMe,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
} from '../api/client';
import { clearTokens, getRefreshToken, removeOldToken, saveTokens } from '../api/tokenStorage';
import type { User } from '../api/types';

/**
 * Vite only exposes env values as strings, so we compare with 'true'.
 * When this is false the whole login feature is switched off: no header links, no routes,
 * and we never even look at localStorage or call /me.
 */
export const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED === 'true';

export interface AuthContextValue {
  user: User | null;
  /** True only while we check the saved tokens with /me on app start. */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * The refresh token is what keeps a session alive: even if the access token has expired,
 * we can still get a new one. So "is there a saved session?" = "is there a refresh token?".
 */
function hasSavedSession(): boolean {
  if (!AUTH_ENABLED) return false;
  return getRefreshToken() !== null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  // If there's a saved session we don't know yet who it belongs to, so we start in "loading".
  // With no session there's nothing to check, so we already know the visitor is a guest.
  const [loading, setLoading] = useState(hasSavedSession);

  useEffect(() => {
    if (!AUTH_ENABLED) return;
    // Clean up the token from the old single-token setup. Users who had one simply sign in again.
    removeOldToken();

    if (!hasSavedSession()) return;

    // AbortController lets us cancel the request if this effect is cleaned up
    // (React StrictMode runs effects twice in development).
    const controller = new AbortController();

    // fetchMe refreshes the access token by itself if it has expired.
    fetchMe(controller.signal)
      .then((me) => {
        setUser(me);
        setLoading(false);
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        // Even a refresh couldn't fix it (or the request failed). Throw the tokens away so
        // we don't keep sending bad ones, and treat the visitor as a guest.
        clearTokens();
        setUser(null);
        setLoading(false);
      });

    return () => controller.abort();
  }, []);

  async function login(email: string, password: string) {
    const response = await loginRequest(email, password);
    saveTokens(response.accessToken, response.refreshToken);
    setUser(response.user);
  }

  async function register(email: string, password: string, displayName: string) {
    const response = await registerRequest(email, password, displayName);
    saveTokens(response.accessToken, response.refreshToken);
    setUser(response.user);
  }

  async function logout() {
    const refreshToken = getRefreshToken();
    try {
      // Ask the backend to revoke the refresh token, so a stolen copy stops working.
      if (refreshToken) await logoutRequest(refreshToken);
    } catch {
      // Server down or token already invalid: not a reason to keep the user signed in.
    } finally {
      // Always clear locally, whatever the server said. Signing out must never "fail".
      clearTokens();
      setUser(null);
    }
  }

  return (
    // Children render straight away, no matter what `loading` is. That's what keeps
    // the search page from ever waiting on auth.
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>{children}</AuthContext.Provider>
  );
}
