import { clearTokens, getAccessToken, getRefreshToken, saveAccessToken } from './tokenStorage';
import type {
  AuthResponse,
  CourseSearchResponse,
  ProblemDetail,
  RefreshResponse,
  SearchParams,
  SourceInfo,
  User,
} from './types';

export const PAGE_SIZE = 12;

/**
 * Backend origin, e.g. https://course-finder-api.onrender.com. Empty in local development,
 * where requests go to /api on the Vite dev server and are proxied to localhost:8080.
 */
const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '');

const UNREACHABLE_MESSAGE = "Can't reach the server. Is the backend running?";

/** Thrown for any failed API call, with a message that is safe to show to users. */
export class ApiError extends Error {
  readonly status: number | null;

  constructor(message: string, status: number | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }

  /** True when the backend couldn't be reached at all (down, or proxy error). */
  get isNetworkError(): boolean {
    return this.status === null || this.status === 502 || this.status === 503 || this.status === 504;
  }

  /** The per-IP search limit was hit (HTTP 429). */
  get isRateLimited(): boolean {
    return this.status === 429;
  }
}

const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please sign in again.';

/**
 * `token` is optional on purpose: only callers that really need to say who the user is
 * (via getJsonWithAuth) pass it. Every other call goes out without an Authorization header.
 */
async function getJson<T>(path: string, signal?: AbortSignal, token?: string): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return sendRequest<T>(path, { signal, headers });
}

/**
 * GET for endpoints that need a logged-in user.
 * Access tokens are short-lived, so a 401 usually just means "your access token expired".
 * Instead of logging the user out, we trade the refresh token for a new access token
 * and try the call ONE more time. Only once, so a broken token can't cause an endless loop.
 */
async function getJsonWithAuth<T>(path: string, signal?: AbortSignal): Promise<T> {
  try {
    return await getJson<T>(path, signal, getAccessToken() ?? undefined);
  } catch (error) {
    // Anything other than a 401 (server down, rate limit, ...) isn't a token problem.
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
  }

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    clearTokens();
    throw new ApiError(SESSION_EXPIRED_MESSAGE, 401);
  }

  try {
    const refreshed = await refreshAccessToken(refreshToken);
    saveAccessToken(refreshed.accessToken);
  } catch {
    // The refresh token is expired, revoked, or the call failed: the session is over.
    // Clearing the tokens is the "log out locally" part; the caller resets the user.
    clearTokens();
    throw new ApiError(SESSION_EXPIRED_MESSAGE, 401);
  }

  // The retry. If this fails too (even with a 401), the error just goes to the caller.
  return getJson<T>(path, signal, getAccessToken() ?? undefined);
}

/** POST a JSON body. Login, register, refresh and logout all send their data this way, without an access token. */
async function postJson<T>(path: string, body: unknown): Promise<T> {
  return sendRequest<T>(path, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

async function sendRequest<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(API_BASE + path, init);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError(UNREACHABLE_MESSAGE, null);
  }

  if (!response.ok) {
    // The backend always answers errors with a problem+json body. A bare 5xx comes from the
    // dev proxy (or a gateway) when the backend itself is down.
    const fromBackend = response.headers.get('Content-Type')?.includes('json') ?? false;
    if (!fromBackend && response.status >= 500) {
      throw new ApiError(UNREACHABLE_MESSAGE, null);
    }
    throw new ApiError(await readProblemMessage(response), response.status);
  }
  // 204 No Content (e.g. /auth/logout) has no body, so there is nothing to parse.
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

async function readProblemMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    const firstFieldError = problem.errors ? Object.values(problem.errors)[0] : undefined;
    return firstFieldError ?? problem.detail ?? problem.title ?? `Request failed (${response.status})`;
  } catch {
    return `Request failed (${response.status})`;
  }
}

export function searchCourses(params: SearchParams, signal?: AbortSignal): Promise<CourseSearchResponse> {
  const query = new URLSearchParams({
    q: params.q,
    page: String(params.page),
    size: String(PAGE_SIZE),
  });
  if (params.source) query.set('source', params.source.toLowerCase());
  if (params.freeOnly) query.set('free', 'true');

  return getJson<CourseSearchResponse>(`/api/courses/search?${query}`, signal);
}

export function fetchSources(signal?: AbortSignal): Promise<SourceInfo[]> {
  return getJson<SourceInfo[]>('/api/sources', signal);
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return postJson<AuthResponse>('/api/auth/login', { email, password });
}

export function register(email: string, password: string, displayName: string): Promise<AuthResponse> {
  return postJson<AuthResponse>('/api/auth/register', { email, password, displayName });
}

/** The refresh token goes in the body, never in the Authorization header. */
export function refreshAccessToken(refreshToken: string): Promise<RefreshResponse> {
  return postJson<RefreshResponse>('/api/auth/refresh', { refreshToken });
}

/** Tells the backend to stop accepting this refresh token. Answers 204 with no body. */
export function logout(refreshToken: string): Promise<void> {
  return postJson<void>('/api/auth/logout', { refreshToken });
}

/** The ONLY call that sends the access token: /me has to know who is asking. */
export function fetchMe(signal?: AbortSignal): Promise<User> {
  return getJsonWithAuth<User>('/api/me', signal);
}
