import { createContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { login as loginRequest, logout as logoutRequest, register as registerRequest } from '../api/client';
import { endSession, refreshOnce, setSessionExpiredHandler, startSession } from '../api/session';
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

/**
 * A BroadcastChannel delivers messages to every other tab of our site that opened a channel
 * with the same name. We use it for one message: "logout".
 */
const AUTH_CHANNEL_NAME = 'courseFinder.auth';
const LOGOUT_MESSAGE = 'logout';

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
  // Kept in a ref so logout() can post on the same channel the effect below opened.
  const channelRef = useRef<BroadcastChannel | null>(null);

  useEffect(() => {
    // Delete old tokens even with the flag off: there's no reason to keep secrets lying around.
    OLD_TOKEN_KEYS.forEach((key) => localStorage.removeItem(key));

    if (!AUTH_ENABLED) return;

    // If a refresh fails later (proactive timer, or a 401 that couldn't be fixed),
    // session.ts calls this so the header and RequireAuth see a guest again.
    setSessionExpiredHandler(() => setUser(null));

    // Another tab signed out: the cookie is already gone, so just forget the user here.
    // We do NOT call /auth/logout again; the tab that sent the message already did.
    const channel = new BroadcastChannel(AUTH_CHANNEL_NAME);
    channel.onmessage = (event: MessageEvent) => {
      if (event.data === LOGOUT_MESSAGE) {
        endSession();
        setUser(null);
      }
    };
    channelRef.current = channel;

    // We can't abort the refresh (see refreshOnce), so instead we ignore its answer if this
    // effect was cleaned up. In development StrictMode runs this effect twice; both runs get
    // the SAME in-flight refresh from refreshOnce, so only one request is sent.
    let ignore = false;

    // Silent check: the browser sends the cookie if there is one.
    refreshOnce()
      .then((session) => {
        if (ignore) return;
        setUser(session.user);
        setLoading(false);
      })
      .catch(() => {
        if (ignore) return;
        // 401 just means "no session", the normal case for a guest. It isn't an error, so we
        // show nothing. Same if the server can't be reached: we treat the visitor as a guest.
        setUser(null);
        setLoading(false);
      });

    return () => {
      ignore = true;
      setSessionExpiredHandler(null);
      channel.close();
      channelRef.current = null;
    };
  }, []);

  async function login(email: string, password: string) {
    // The backend also sets the refresh cookie on this response; the browser stores it for us.
    const response = await loginRequest(email, password);
    startSession(response);
    setUser(response.user);
  }

  async function register(email: string, password: string, displayName: string) {
    const response = await registerRequest(email, password, displayName);
    startSession(response);
    setUser(response.user);
  }

  async function logout() {
    try {
      // Only the backend can clear an httpOnly cookie, so this call is the real sign-out.
      await logoutRequest();
    } catch {
      // Server down: not a reason to keep the user signed in on this page.
    } finally {
      // Always forget the user locally, whatever the server said...
      endSession();
      setUser(null);
      // ...and tell the other tabs. A channel never receives its own messages, so this tab
      // doesn't react to it.
      channelRef.current?.postMessage(LOGOUT_MESSAGE);
    }
  }

  return (
    // Children render straight away, no matter what `loading` is. That's what keeps
    // the search page from ever waiting on auth.
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>{children}</AuthContext.Provider>
  );
}
