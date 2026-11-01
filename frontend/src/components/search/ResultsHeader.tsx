import { formatNumber } from '../../utils/format';
import { MonoLabel } from '../ui/MonoLabel';
import styles from './ResultsHeader.module.css';

interface ResultsHeaderProps {
  query: string;
  total: number;
  fuzzyMatch: boolean;
}

/** The big blue count, styled like the reel's "985,231 / STEPS · WORST CASE" counter. */
export function ResultsHeader({ query, total, fuzzyMatch }: ResultsHeaderProps) {
  return (
    <div className={styles.header}>
      <div className={styles.count} aria-live="polite">
        <span className={styles.number}>{formatNumber(total)}</span>
        <MonoLabel as="span">{total === 1 ? 'result' : 'results'}</MonoLabel>
      </div>
      {fuzzyMatch ? (
        <p className={styles.note}>
          <MonoLabel as="span" tone="accent">
            Similar titles
          </MonoLabel>
          No exact match for “{query}”, so these are close spellings.
        </p>
      ) : (
        <p className={styles.query}>for “{query}”</p>
      )}
    </div>
  );
}
