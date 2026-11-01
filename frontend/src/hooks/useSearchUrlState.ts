import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router';
import type { CourseSource, SearchParams } from '../api/types';

const SOURCES: CourseSource[] = ['COURSERA', 'YOUTUBE'];

function parseSource(value: string | null): CourseSource | null {
  const upper = value?.toUpperCase();
  return SOURCES.find((source) => source === upper) ?? null;
}

function parsePage(value: string | null): number {
  const page = Number.parseInt(value ?? '', 10);
  return Number.isFinite(page) && page > 1 ? page - 1 : 0;
}

/**
 * The search lives in the URL, so every result page is shareable:
 * {@code /?q=machine+learning&source=youtube&free=1&page=2}. The page number in the URL is 1-based.
 */
export function useSearchUrlState() {
  const [searchParams, setSearchParams] = useSearchParams();

  const params: SearchParams = useMemo(
    () => ({
      q: searchParams.get('q')?.trim() ?? '',
      source: parseSource(searchParams.get('source')),
      freeOnly: searchParams.get('free') === '1',
      page: parsePage(searchParams.get('page')),
    }),
    [searchParams],
  );

  const update = useCallback(
    (changes: Partial<SearchParams>, options: { replace?: boolean } = {}) => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current);
          const set = (key: string, value: string | null) => (value ? next.set(key, value) : next.delete(key));

          if ('q' in changes) set('q', changes.q?.trim() || null);
          if ('source' in changes) set('source', changes.source?.toLowerCase() ?? null);
          if ('freeOnly' in changes) set('free', changes.freeOnly ? '1' : null);

          // Any change other than the page itself starts again from page 1.
          const page = 'page' in changes ? changes.page ?? 0 : 0;
          set('page', page > 0 ? String(page + 1) : null);
          return next;
        },
        { replace: options.replace },
      );
    },
    [setSearchParams],
  );

  return { params, update };
}
