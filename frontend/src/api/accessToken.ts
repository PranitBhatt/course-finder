/**
 * The access token lives ONLY in this variable, in memory.
 *
 * - Nothing is written to localStorage, so the token disappears when the tab closes or reloads.
 *   That's fine: on startup we get a fresh one from /auth/refresh using the httpOnly cookie.
 * - It's a plain module variable rather than React state because the API client (which isn't a
 *   React component) needs to read it. Like a useRef, it's just a value in memory, and changing
 *   it doesn't re-render anything.
 */
let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string) {
  accessToken = token;
}

export function clearAccessToken() {
  accessToken = null;
}
