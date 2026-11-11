/**
 * Every read and write of the tokens goes through this file, so the localStorage keys
 * live in exactly one place.
 */

// Short-lived token sent as "Authorization: Bearer" to endpoints that need a logged-in user.
const ACCESS_TOKEN_KEY = 'courseFinder.accessToken';
// Long-lived token used ONLY to ask /auth/refresh for a new access token (and to log out).
const REFRESH_TOKEN_KEY = 'courseFinder.refreshToken';
// The single token we stored before access + refresh tokens existed. The backend no longer
// issues tokens in that shape, so it is useless now and we just delete it.
const OLD_TOKEN_KEY = 'courseFinder.token';

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

/** After login/register we get both tokens at once. */
export function saveTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

/** /auth/refresh only gives us a new access token; the refresh token we have stays the same. */
export function saveAccessToken(accessToken: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function removeOldToken() {
  localStorage.removeItem(OLD_TOKEN_KEY);
}
