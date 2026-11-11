import { useCallback, useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/client';
import {
  fetchProgress,
  fetchProgressSummary,
  removeProgressEntry,
  saveProgress,
  STATUS_LABELS,
} from '../../api/progress';
import type { ProgressItem, ProgressPage, ProgressStatus, ProgressSummary, ProgressUpdate } from '../../api/types';
import buttonStyles from '../../components/feedback/Button.module.css';
import { ErrorState } from '../../components/feedback/ErrorState';
import { StatePanel } from '../../components/feedback/StatePanel';
import { Pagination } from '../../components/pagination/Pagination';
import filterStyles from '../../components/search/Filters.module.css';
import { MonoLabel } from '../../components/ui/MonoLabel';
import { SITE_NAME } from '../../utils/site';
import styles from './LearningPage.module.css';

const FILTERS: { value: ProgressStatus | null; label: string }[] = [
  { value: null, label: 'All' },
  { value: 'PLANNED', label: STATUS_LABELS.PLANNED },
  { value: 'IN_PROGRESS', label: STATUS_LABELS.IN_PROGRESS },
  { value: 'COMPLETED', label: STATUS_LABELS.COMPLETED },
];

// Wait this long after the slider stops moving before saving, so dragging sends one request, not fifty.
const PERCENT_SAVE_DELAY_MS = 600;

type ListState =
  | { status: 'loading' }
  | { status: 'error'; error: ApiError }
  | { status: 'success'; data: ProgressPage };

/** "My Learning": always rendered inside <RequireAuth>. */
export function LearningPage() {
  const [filter, setFilter] = useState<ProgressStatus | null>(null);
  const [page, setPage] = useState(0);
  const [list, setList] = useState<ListState>({ status: 'loading' });
  const [summary, setSummary] = useState<ProgressSummary | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    document.title = `My Learning | ${SITE_NAME}`;
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setList({ status: 'loading' });
    fetchProgress(filter, page, controller.signal)
      .then((data) => setList({ status: 'success', data }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setList({ status: 'error', error: error instanceof ApiError ? error : new ApiError('Something went wrong.', null) });
      });
    return () => controller.abort();
  }, [filter, page, attempt]);

  const reloadSummary = useCallback(() => {
    fetchProgressSummary()
      .then(setSummary)
      .catch(() => {
        // The counts are extra information; the list below still works without them.
      });
  }, []);

  useEffect(reloadSummary, [reloadSummary]);

  const changeFilter = (next: ProgressStatus | null) => {
    setFilter(next);
    setPage(0);
  };

  // Stable callbacks: rows use them in effect dependencies (the debounced slider save).
  const replaceItem = useCallback(
    (updated: ProgressItem) => {
      setList((current) => {
        if (current.status !== 'success') return current;
        const content = current.data.content.map((item) => (item.entryId === updated.entryId ? updated : item));
        return { status: 'success', data: { ...current.data, content } };
      });
      reloadSummary();
    },
    [reloadSummary],
  );

  const removeItem = useCallback((entryId: string) => {
    setList((current) => {
      if (current.status !== 'success') return current;
      const content = current.data.content.filter((item) => item.entryId !== entryId);
      return {
        status: 'success',
        data: { ...current.data, content, totalElements: Math.max(0, current.data.totalElements - 1) },
      };
    });
    reloadSummary();
  }, [reloadSummary]);

  return (
    <main className={styles.page}>
      <header className={styles.head}>
        <MonoLabel tone="accent">My Learning</MonoLabel>
        <h1 className={styles.title}>Your courses</h1>
      </header>

      <p className={styles.notice}>
        Progress tracking is free during early access. In the future it will become part of a paid plan — we'll let you
        know before anything changes.
      </p>

      <dl className={styles.summary} aria-label="Summary">
        <SummaryItem label="Planned" value={summary?.planned} />
        <SummaryItem label="In progress" value={summary?.inProgress} />
        <SummaryItem label="Completed" value={summary?.completed} />
      </dl>

      <div className={filterStyles.segmented} role="group" aria-label="Filter by status">
        {FILTERS.map((option) => (
          <button
            key={option.label}
            type="button"
            className={filterStyles.segment}
            aria-pressed={filter === option.value}
            onClick={() => changeFilter(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

      <section className={styles.list} aria-label="Tracked courses" aria-busy={list.status === 'loading' || undefined}>
        {list.status === 'loading' && <p className={styles.loading}>Loading your courses…</p>}

        {list.status === 'error' && <ErrorState error={list.error} onRetry={() => setAttempt((n) => n + 1)} />}

        {list.status === 'success' && list.data.content.length === 0 && (
          <StatePanel
            label="My Learning"
            role="status"
            title={filter === null ? 'Nothing tracked yet.' : `No ${STATUS_LABELS[filter].toLowerCase()} courses.`}
          >
            <p>Find a course, then use “Track” on its card to add it here.</p>
            <Link to="/" className={buttonStyles.primary}>
              Search courses
            </Link>
          </StatePanel>
        )}

        {list.status === 'success' && list.data.content.length > 0 && (
          <>
            <ul className={styles.items}>
              {list.data.content.map((item) => (
                <li key={item.entryId}>
                  <ProgressRow item={item} onSaved={replaceItem} onRemoved={removeItem} />
                </li>
              ))}
            </ul>
            <Pagination
              page={list.data.page}
              totalPages={list.data.totalPages}
              onPageChange={setPage}
              label="My Learning pages"
            />
          </>
        )}
      </section>
    </main>
  );
}

function SummaryItem({ label, value }: { label: string; value: number | undefined }) {
  return (
    <div className={styles.summaryItem}>
      <dt>{label}</dt>
      <dd>{value ?? '–'}</dd>
    </div>
  );
}

interface ProgressRowProps {
  item: ProgressItem;
  onSaved: (item: ProgressItem) => void;
  onRemoved: (entryId: string) => void;
}

function ProgressRow({ item, onSaved, onRemoved }: ProgressRowProps) {
  const ids = useId();
  const [percent, setPercent] = useState(item.progressPercent);
  const [notes, setNotes] = useState(item.notes ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A saved answer (e.g. COMPLETED forcing 100%) wins over what's on screen.
  useEffect(() => setPercent(item.progressPercent), [item.progressPercent]);
  useEffect(() => setNotes(item.notes ?? ''), [item.notes]);

  // Entries whose course was removed can only be removed: there's no course left to update.
  const editable = item.courseAvailable && item.courseId !== null;

  const save = useCallback(
    async (update: ProgressUpdate) => {
      if (item.courseId === null) return;
      setBusy(true);
      setError(null);
      try {
        onSaved(await saveProgress(item.courseId, update));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Couldn't save. Try again.");
        // Put the controls back to what is really stored.
        setPercent(item.progressPercent);
      } finally {
        setBusy(false);
      }
    },
    [item.courseId, item.progressPercent, onSaved],
  );

  // Debounced slider save.
  useEffect(() => {
    if (!editable || percent === item.progressPercent) return;
    const timer = setTimeout(() => save({ status: item.status, progressPercent: percent }), PERCENT_SAVE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [editable, percent, item.progressPercent, item.status, save]);

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await removeProgressEntry(item.entryId);
      onRemoved(item.entryId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't remove. Try again.");
      setBusy(false);
    }
  }

  const notesDirty = notes.trim() !== (item.notes ?? '');

  return (
    <article className={styles.item} data-status={item.status}>
      <div className={styles.itemHead}>
        <div className={styles.itemTitleBlock}>
          <span className={styles.source}>{item.courseSourceName}</span>
          {editable ? (
            <h2 className={styles.itemTitle}>
              <a href={item.course?.url ?? item.courseUrl} target="_blank" rel="noopener noreferrer">
                {item.course?.title ?? item.courseTitle}
                <span className="visually-hidden"> (opens {item.courseSourceName} in a new tab)</span>
              </a>
            </h2>
          ) : (
            <>
              <h2 className={styles.itemTitle}>{item.courseTitle}</h2>
              <span className={styles.unavailable}>No longer available</span>
            </>
          )}
        </div>
        <button type="button" className={styles.remove} onClick={remove} disabled={busy}>
          Remove<span className="visually-hidden"> “{item.courseTitle}” from My Learning</span>
        </button>
      </div>

      <div className={styles.controls}>
        <label className={styles.field} htmlFor={`${ids}-status`}>
          Status
          <select
            id={`${ids}-status`}
            className={styles.input}
            value={item.status}
            disabled={!editable || busy}
            onChange={(event) => save({ status: event.target.value as ProgressStatus })}
          >
            {(Object.keys(STATUS_LABELS) as ProgressStatus[]).map((status) => (
              <option key={status} value={status}>
                {STATUS_LABELS[status]}
              </option>
            ))}
          </select>
        </label>

        <div className={styles.field}>
          <label htmlFor={`${ids}-percent`}>Progress</label>
          <div className={styles.percentRow}>
            <input
              id={`${ids}-percent`}
              type="range"
              min={0}
              max={100}
              step={5}
              value={percent}
              // Completed always means 100%; change the status to edit it.
              disabled={!editable || item.status === 'COMPLETED'}
              onChange={(event) => setPercent(Number(event.target.value))}
              aria-valuetext={`${percent}%`}
            />
            <output htmlFor={`${ids}-percent`} className={styles.percentValue}>
              {percent}%
            </output>
          </div>
        </div>
      </div>

      <div className={styles.field}>
        <label htmlFor={`${ids}-notes`}>Notes</label>
        <textarea
          id={`${ids}-notes`}
          className={`${styles.input} ${styles.notes}`}
          maxLength={500}
          rows={2}
          value={notes}
          disabled={!editable}
          onChange={(event) => setNotes(event.target.value)}
        />
        {editable && notesDirty && (
          <button
            type="button"
            className={`${buttonStyles.secondary} ${styles.saveNotes}`}
            disabled={busy}
            onClick={() => save({ status: item.status, notes: notes.trim() })}
          >
            Save notes
          </button>
        )}
      </div>

      <div className={styles.meta}>
        <span>Last updated {new Date(item.updatedAt).toLocaleDateString()}</span>
        {error && (
          <span className={styles.error} role="alert">
            {error}
          </span>
        )}
      </div>
    </article>
  );
}
