import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { lookupProgress, removeProgress, saveProgress } from '../api/progress';
import type { ProgressStatus, ProgressStatusInfo } from '../api/types';
import { AUTH_ENABLED } from '../context/AuthContext';
import { useAuth } from './useAuth';

const ERROR_VISIBLE_MS = 4000;

export interface ProgressLookup {
  /** false until the lookup for the current page answered (or failed) */
  loaded: boolean;
  statuses: Record<number, ProgressStatusInfo>;
  errors: Record<number, string>;
  /** null = stop tracking */
  setStatus: (courseId: number, status: ProgressStatus | null) => void;
}

/**
 * Track status for the cards on one search results page.
 *
 * ONE lookup request per results page, after the results are already on screen, so search never
 * waits for it. Changes are optimistic: the badge changes at once, and goes back (with a short
 * message) if the server says no.
 */
export function useProgressLookup(courseIds: number[]): ProgressLookup {
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const [statuses, setStatuses] = useState<Record<number, ProgressStatusInfo>>({});
  const [errors, setErrors] = useState<Record<number, string>>({});
  const [loaded, setLoaded] = useState(false);
  // Latest request number per course: a slow older answer must not overwrite a newer choice.
  const requestSeq = useRef<Record<number, number>>({});
  // Mirror of `statuses`, so setStatus can read the current value without being re-created.
  const statusesRef = useRef(statuses);
  statusesRef.current = statuses;

  // Same ids in the same order = same page; the array itself is new on every render.
  const idsKey = courseIds.join(',');

  useEffect(() => {
    setStatuses({});
    setErrors({});
    setLoaded(false);
    if (!AUTH_ENABLED || userId === null || !idsKey) return;

    const controller = new AbortController();
    lookupProgress(idsKey.split(',').map(Number), controller.signal)
      .then((result) => {
        setStatuses(result);
        setLoaded(true);
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        // Badges are a nice-to-have: on failure the cards just show "Track" and still work.
        setLoaded(true);
      });
    return () => controller.abort();
  }, [idsKey, userId]);

  const showError = useCallback((courseId: number, message: string) => {
    setErrors((current) => ({ ...current, [courseId]: message }));
    setTimeout(() => {
      setErrors((current) => {
        if (current[courseId] !== message) return current;
        const { [courseId]: _removed, ...rest } = current;
        return rest;
      });
    }, ERROR_VISIBLE_MS);
  }, []);

  const setStatus = useCallback(
    (courseId: number, status: ProgressStatus | null) => {
      const previous = statusesRef.current[courseId];
      const seq = (requestSeq.current[courseId] ?? 0) + 1;
      requestSeq.current[courseId] = seq;
      const isLatest = () => requestSeq.current[courseId] === seq;

      const apply = (info: ProgressStatusInfo | undefined) =>
        setStatuses((current) => {
          const { [courseId]: _old, ...rest } = current;
          return info ? { ...rest, [courseId]: info } : rest;
        });

      // Optimistic: show the choice right away. The percent is a guess until the server answers.
      apply(
        status === null
          ? undefined
          : { status, progressPercent: status === 'COMPLETED' ? 100 : (previous?.progressPercent ?? 0) },
      );

      const request =
        status === null
          ? removeProgress(courseId).then(() => undefined)
          : saveProgress(courseId, { status }).then((item) => ({
              status: item.status,
              progressPercent: item.progressPercent,
            }));

      request
        .then((info) => {
          if (isLatest()) apply(info);
        })
        .catch((error: unknown) => {
          if (!isLatest()) return;
          apply(previous); // roll back
          showError(courseId, error instanceof ApiError ? error.message : "Couldn't save. Try again.");
        });
    },
    [showError],
  );

  return { loaded, statuses, errors, setStatus };
}
