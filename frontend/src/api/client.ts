import type { AuthResponse, CourseSearchResponse, ProblemDetail, SearchParams, SourceInfo, User } from './types';

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

/**
 * `token` is optional on purpose: only callers that really need to say who the user is
 * (right now just fetchMe) pass it. Every other call goes out without an Authorization header.
 */
async function getJson<T>(path: string, signal?: AbortSignal, token?: string): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return sendRequest<T>(path, { signal, headers });
}

/** POST a JSON body. Used for login and register, which don't need a token (that's how you get one). */
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

/** The ONLY call that sends the token: /me has to know who is asking. */
export function fetchMe(token: string, signal?: AbortSignal): Promise<User> {
  return getJson<User>('/api/me', signal, token);
}
