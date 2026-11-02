import { useEffect, useState } from 'react';
import { MonoLabel } from '../ui/MonoLabel';
import styles from './SlowLoadingNotice.module.css';

const SHOW_AFTER_MS = 4000;

/**
 * Appears if a search is still loading after a few seconds. The free backend sleeps when idle
 * and needs up to a minute to wake up, so this tells people it's working, not broken.
 */
export function SlowLoadingNotice() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setVisible(true), SHOW_AFTER_MS);
    return () => window.clearTimeout(timer);
  }, []);

  if (!visible) return null;

  return (
    <p className={styles.notice} role="status">
      <MonoLabel as="span" tone="accent">
        Waking up
      </MonoLabel>
      The server naps when nobody is searching. The first search can take up to a minute.
    </p>
  );
}
