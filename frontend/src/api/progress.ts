import { requestWithAuth } from './session';
import type { ProgressItem, ProgressPage, ProgressStatus, ProgressStatusInfo, ProgressSummary, ProgressUpdate } from './types';

export const PROGRESS_PAGE_SIZE = 20;

export const STATUS_LABELS: Record<ProgressStatus, string> = {
  PLANNED: 'Planned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
};

export function fetchProgress(status: ProgressStatus | null, page: number, signal?: AbortSignal): Promise<ProgressPage> {
  const query = new URLSearchParams({ page: String(page), size: String(PROGRESS_PAGE_SIZE) });
  if (status) query.set('status', status);
  return requestWithAuth<ProgressPage>('GET', `/api/me/progress?${query}`, { signal });
}

export function fetchProgressSummary(signal?: AbortSignal): Promise<ProgressSummary> {
  return requestWithAuth<ProgressSummary>('GET', '/api/me/progress/summary', { signal });
}

/**
 * Status for all cards of one results page in a single request (instead of one per card).
 * JSON object keys are always strings, so the ids come back as "42" and are turned into numbers.
 */
export async function lookupProgress(courseIds: number[], signal?: AbortSignal): Promise<Record<number, ProgressStatusInfo>> {
  const raw = await requestWithAuth<Record<string, ProgressStatusInfo>>(
    'GET',
    `/api/me/progress/lookup?courseIds=${courseIds.join(',')}`,
    { signal },
  );
  return Object.fromEntries(Object.entries(raw).map(([id, info]) => [Number(id), info]));
}

export function saveProgress(courseId: number, update: ProgressUpdate): Promise<ProgressItem> {
  return requestWithAuth<ProgressItem>('PUT', `/api/me/progress/${courseId}`, { body: update });
}

export function removeProgress(courseId: number): Promise<void> {
  return requestWithAuth<void>('DELETE', `/api/me/progress/${courseId}`);
}

/** By entry id: also works for entries whose course was removed from the catalog. */
export function removeProgressEntry(entryId: string): Promise<void> {
  return requestWithAuth<void>('DELETE', `/api/me/progress/entries/${encodeURIComponent(entryId)}`);
}
