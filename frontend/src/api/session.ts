import { clearAccessToken, getAccessToken, setAccessToken } from './accessToken';
import { ApiError, getJson, refreshSession } from './client';
import type { AuthResponse, User } from './types';

/*
 * Everything that refreshes the session goes through refreshOnce() in this file.
 *
 * Why so careful? The backend ROTATES refresh tokens: every /auth/refresh answers with a new
 * cookie and retires the old one. If two refresh calls leave with the SAME cookie, the second
 * one is "reusing" a retired token. Within ~10s the backend just answers 401; after that it
 * treats it as theft and kills the session everywhere. So we must never send two at once.
 */

const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please sign in again.';

// Any name works, as long as every tab uses the same one.
const REFRESH_LOCK_NAME = 'courseFinder.refresh';

// Refresh this many seconds before the access token runs out, so requests rarely hit a 401.
const REFRESH_EARLY_SECONDS = 60;

/** The refresh that is happening right now in this tab, if any. Shared by everyone who asks. */
let refreshInFlight: Promise<AuthResponse> | null = null;

/** Timer for the next proactive refresh. */
let refreshTimer: ReturnType<typeof setTimeout> | null = null;

/** Set by AuthProvider so this file can tell React "the session is over, forget the user". */
let onSessionExpired: (() => void) | null = null;

export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler;
}

/** Call after login/register/refresh: keep the access token and plan the next refresh. */
export function startSession(response: AuthResponse) {
  setAccessToken(response.accessToken);
  scheduleProactiveRefresh(response.expiresIn);
}

/** Forget the access token and cancel the planned refresh. */
export function endSession() {
  clearAccessToken();
  if (refreshTimer) clearTimeout(refreshTimer);
  refreshTimer = null;
}

/**
 * Single-flight refresh: the first caller starts /auth/refresh, everyone who calls while it
 * is still running gets the SAME promise back. Five callers, one request.
 *
 * There's deliberately no AbortSignal here: one caller giving up must not cancel the refresh
 * for everybody else. Aborting halfway is also risky with rotation: the server may already
 * have rotated the cookie when we stop listening.
 */
export function refreshOnce(): Promise<AuthResponse> {
  if (!refreshInFlight) {
    refreshInFlight = refreshWithLock()
      .then((response) => {
        startSession(response);
        return response;
      })
      .catch((error: unknown) => {
        endSession();
        throw error;
      })
      .finally(() => {
        // Done (either way): the next caller may start a fresh refresh.
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

/**
 * refreshInFlight only protects ONE tab: each tab has its own copy of this file's variables.
 * The cookie, though, is shared by all tabs. The Web Locks API gives us a lock that every tab
 * of our site respects: while one tab holds it, the others wait their turn. When the next tab
 * gets the lock, the browser already has the NEW cookie, so its refresh is valid, not a reuse.
 */
async function refreshWithLock(): Promise<AuthResponse> {
  // Very old browsers, and pages not on https/localhost, have no navigator.locks.
  // Then we refresh without the cross-tab lock (single-flight still works inside the tab).
  if (!('locks' in navigator)) return refreshSession();
  // The lock is held until refreshSession() finishes, i.e. until the new cookie is stored.
  return await navigator.locks.request(REFRESH_LOCK_NAME, () => refreshSession());
}

function scheduleProactiveRefresh(expiresInSeconds: number) {
  if (refreshTimer) clearTimeout(refreshTimer);
  // Never less than 10 seconds, so a very short-lived token can't make us refresh in a loop.
  const delaySeconds = Math.max(expiresInSeconds - REFRESH_EARLY_SECONDS, 10);
  refreshTimer = setTimeout(() => {
    // Goes through refreshOnce too, so it can't overlap with a 401-triggered refresh.
    refreshOnce().catch(() => onSessionExpired?.());
  }, delaySeconds * 1000);
}

/**
 * GET for endpoints that need a logged-in user. On a 401 we refresh (shared, see above) and
 * retry ONCE.
 */
async function getJsonWithAuth<T>(path: string, signal?: AbortSignal): Promise<T> {
  // Remember which token this request used...
  const tokenUsed = getAccessToken();
  try {
    return await getJson<T>(path, signal, tokenUsed ?? undefined);
  } catch (error) {
    // Anything other than a 401 (server down, rate limit, ...) isn't a token problem.
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
  }

  // ...because if the token changed while we waited, another request already refreshed.
  // Then we just retry with the new token instead of refreshing (and rotating) again.
  if (getAccessToken() === tokenUsed) {
    try {
      await refreshOnce();
    } catch {
      // The session is over. refreshOnce already forgot the token; tell React too.
      onSessionExpired?.();
      throw new ApiError(SESSION_EXPIRED_MESSAGE, 401);
    }
  }

  // The retry. If this fails too (even with a 401), the error just goes to the caller.
  return getJson<T>(path, signal, getAccessToken() ?? undefined);
}

/** The only call that sends the access token: /me has to know who is asking. */
export function fetchMe(signal?: AbortSignal): Promise<User> {
  return getJsonWithAuth<User>('/api/me', signal);
}
