import { useId, type ChangeEvent } from 'react';
import { Link, useLocation } from 'react-router';
import { STATUS_LABELS } from '../../api/progress';
import type { ProgressStatus, ProgressStatusInfo } from '../../api/types';
import { AUTH_ENABLED } from '../../context/AuthContext';
import { useAuth } from '../../hooks/useAuth';
import styles from './TrackControl.module.css';

const REMOVE = 'REMOVE';
const UNTRACKED = '';

interface TrackControlProps {
  courseTitle: string;
  info: ProgressStatusInfo | undefined;
  /** false while the page's single lookup request is still running */
  loaded: boolean;
  error: string | undefined;
  onChange: (status: ProgressStatus | null) => void;
}

/**
 * "Track" on a search result card. A native <select>, so keyboard and screen readers work
 * out of the box. Guests get a sign-in link that brings them back to these results.
 */
export function TrackControl({ courseTitle, info, loaded, error, onChange }: TrackControlProps) {
  const { user, loading } = useAuth();
  const location = useLocation();
  const errorId = useId();

  // With the login feature off, /login doesn't exist, so there's nothing to offer.
  // While the silent sign-in check runs we don't know yet who this is: show nothing.
  if (!AUTH_ENABLED || loading) return null;

  if (!user) {
    const next = `${location.pathname}${location.search}`;
    return (
      <Link to={`/login?next=${encodeURIComponent(next)}`} className={styles.control}>
        Sign in to track
      </Link>
    );
  }

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    const value = event.target.value;
    onChange(value === REMOVE ? null : (value as ProgressStatus));
  }

  const value = info?.status ?? UNTRACKED;

  return (
    <span className={styles.wrap}>
      <select
        className={`${styles.control} ${styles.select}`}
        data-status={info?.status ?? 'NONE'}
        value={value}
        onChange={handleChange}
        disabled={!loaded}
        aria-label={`Track “${courseTitle}”`}
        aria-describedby={error ? errorId : undefined}
      >
        {!info && <option value={UNTRACKED}>{loaded ? 'Track' : 'Track…'}</option>}
        {(Object.keys(STATUS_LABELS) as ProgressStatus[]).map((status) => (
          <option key={status} value={status}>
            {STATUS_LABELS[status]}
          </option>
        ))}
        {info && <option value={REMOVE}>Remove</option>}
      </select>
      {error && (
        <span id={errorId} className={styles.error} role="alert">
          {error}
        </span>
      )}
    </span>
  );
}
