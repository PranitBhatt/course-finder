import { clearAccessToken, getAccessToken, setAccessToken } from './accessToken';
import type { AuthResponse, CourseSearchResponse, ProblemDetail, SearchParams, SourceInfo, User } from './types';

export const PAGE_SIZE = 12;

/*
 * Every URL below is RELATIVE (/api/...), so the browser always calls our own site:
 *  - locally, the Vite dev server proxies /api to localhost:8080 (vite.config.ts)
 *  - in production, Vercel proxies /api to Render (the rewrite in vercel.json)
 * Because the API and the page share an origin, the refresh cookie is a normal first-party
 * cookie. If the browser called the Render domain directly, the cookie would be third-party,
 * and browsers block those.
 */

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
 *
 * credentials: 'omit' means "send no cookies". Our API is same-origin now, so by default the
 * browser WOULD attach the refresh cookie to every request, search included. GET calls never
 * need it (/me uses the Bearer header), so we keep the cookie away from them.
 */
async function getJson<T>(path: string, signal?: AbortSignal, token?: string): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return sendRequest<T>(path, { signal, headers, credentials: 'omit' });
}

/**
 * GET for endpoints that need a logged-in user.
 * Access tokens are short-lived, so a 401 usually just means "your access token expired".
 * We ask /auth/refresh for a new one (the browser sends the cookie) and try the call ONE
 * more time. Only once, so a broken session can't cause an endless loop.
 */
async function getJsonWithAuth<T>(path: string, signal?: AbortSignal): Promise<T> {
  try {
    return await getJson<T>(path, signal, getAccessToken() ?? undefined);
  } catch (error) {
    // Anything other than a 401 (server down, rate limit, ...) isn't a token problem.
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
  }

  try {
    const refreshed = await refreshSession();
    setAccessToken(refreshed.accessToken);
  } catch {
    // The cookie is missing, expired or revoked, or the call failed: the session is over.
    // Forgetting the access token is the "log out locally" part; the caller resets the user.
    clearAccessToken();
    throw new ApiError(SESSION_EXPIRED_MESSAGE, 401);
  }

  // The retry. If this fails too (even with a 401), the error just goes to the caller.
  return getJson<T>(path, signal, getAccessToken() ?? undefined);
}

/**
 * POST to the auth endpoints. `body` is optional because refresh and logout send nothing:
 * the httpOnly cookie is all the backend needs.
 *
 * credentials: 'same-origin' tells fetch to send cookies with the request AND to store the
 * Set-Cookie from the response, as long as the URL is on our own origin. It's already fetch's
 * default; we write it out because these are exactly the calls that depend on the cookie.
 * We don't use 'include': that's for cross-site requests, which we no longer make.
 */
async function postJson<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  return sendRequest<T>(path, {
    method: 'POST',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
    signal,
  });
}

async function sendRequest<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, init);
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

/** Besides the JSON, the response sets the httpOnly refresh cookie. */
export function login(email: string, password: string): Promise<AuthResponse> {
  return postJson<AuthResponse>('/api/auth/login', { email, password });
}

/** Besides the JSON, the response sets the httpOnly refresh cookie. */
export function register(email: string, password: string, displayName: string): Promise<AuthResponse> {
  return postJson<AuthResponse>('/api/auth/register', { email, password, displayName });
}

/**
 * No body: the browser attaches the httpOnly cookie, which our JavaScript can't even read.
 * 200 gives a new access token plus the user; 401 means there is no valid session.
 */
export function refreshSession(signal?: AbortSignal): Promise<AuthResponse> {
  return postJson<AuthResponse>('/api/auth/refresh', undefined, signal);
}

/** The backend revokes the session and clears the cookie. Answers 204 with no body. */
export function logout(): Promise<void> {
  return postJson<void>('/api/auth/logout');
}

/** The ONLY call that sends the access token: /me has to know who is asking. */
export function fetchMe(signal?: AbortSignal): Promise<User> {
  return getJsonWithAuth<User>('/api/me', signal);
}
