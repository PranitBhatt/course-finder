import type { CourseSearchResponse, ProblemDetail, SearchParams, SourceInfo } from './types';

export const PAGE_SIZE = 12;

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
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, { signal, headers: { Accept: 'application/json' } });
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
