import { useCallback, useEffect, useState } from 'react';
import { ApiError, searchCourses } from '../api/client';
import type { CourseSearchResponse, SearchParams } from '../api/types';

export type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: CourseSearchResponse }
  | { status: 'error'; error: ApiError };

/**
 * Runs a search whenever the params change. An in-flight request is cancelled when a newer
 * one starts, so results never arrive out of order.
 */
export function useCourseSearch(params: SearchParams): { state: SearchState; retry: () => void } {
  const [state, setState] = useState<SearchState>({ status: 'idle' });
  const [attempt, setAttempt] = useState(0);

  const { q, source, freeOnly, page } = params;

  useEffect(() => {
    if (!q) {
      setState({ status: 'idle' });
      return;
    }

    const controller = new AbortController();
    setState({ status: 'loading' });

    searchCourses({ q, source, freeOnly, page }, controller.signal)
      .then((data) => setState({ status: 'success', data }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        const apiError = error instanceof ApiError ? error : new ApiError('Something went wrong.', null);
        setState({ status: 'error', error: apiError });
      });

    return () => controller.abort();
  }, [q, source, freeOnly, page, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  return { state, retry };
}
