import { createContext, useEffect, useState, type ReactNode } from 'react';
import { fetchMe, login as loginRequest, register as registerRequest } from '../api/client';
import type { User } from '../api/types';

/**
 * Vite only exposes env values as strings, so we compare with 'true'.
 * When this is false the whole login feature is switched off: no header links, no routes,
 * and we never even look at localStorage or call /me.
 */
export const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED === 'true';

// The single place we keep the token. Every read/write of it goes through this key.
const TOKEN_KEY = 'courseFinder.token';

export interface AuthContextValue {
  user: User | null;
  /** True only while we check a saved token with /me on app start. */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

function readSavedToken(): string | null {
  if (!AUTH_ENABLED) return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  // If there's a saved token we don't know yet who it belongs to, so we start in "loading".
  // With no token there's nothing to check, so we already know the visitor is a guest.
  const [loading, setLoading] = useState(() => readSavedToken() !== null);

  useEffect(() => {
    const token = readSavedToken();
    if (!token) return;

    // AbortController lets us cancel the request if this effect is cleaned up
    // (React StrictMode runs effects twice in development).
    const controller = new AbortController();

    fetchMe(token, controller.signal)
      .then((me) => {
        setUser(me);
        setLoading(false);
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        // The token is expired, invalid, or the request failed. Throw it away so we
        // don't keep sending a bad token, and treat the visitor as a guest.
        localStorage.removeItem(TOKEN_KEY);
        setUser(null);
        setLoading(false);
      });

    return () => controller.abort();
  }, []);

  async function login(email: string, password: string) {
    const response = await loginRequest(email, password);
    localStorage.setItem(TOKEN_KEY, response.token);
    setUser(response.user);
  }

  async function register(email: string, password: string, displayName: string) {
    const response = await registerRequest(email, password, displayName);
    localStorage.setItem(TOKEN_KEY, response.token);
    setUser(response.user);
  }

  function logout() {
    // There is no server call here: the token simply stops being sent. See the
    // "known weaknesses" — the token itself is still valid until it expires.
    localStorage.removeItem(TOKEN_KEY);
    setUser(null);
  }

  return (
    // Children render straight away, no matter what `loading` is. That's what keeps
    // the search page from ever waiting on auth.
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>{children}</AuthContext.Provider>
  );
}
