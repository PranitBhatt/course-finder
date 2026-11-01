import type { CourseSource } from '../../api/types';
import styles from './Filters.module.css';

const SOURCE_OPTIONS: { value: CourseSource | null; label: string }[] = [
  { value: null, label: 'All' },
  { value: 'COURSERA', label: 'Coursera' },
  { value: 'YOUTUBE', label: 'YouTube' },
];

interface FiltersProps {
  source: CourseSource | null;
  freeOnly: boolean;
  onSourceChange: (source: CourseSource | null) => void;
  onFreeOnlyChange: (freeOnly: boolean) => void;
}

export function Filters({ source, freeOnly, onSourceChange, onFreeOnlyChange }: FiltersProps) {
  return (
    <div className={styles.filters}>
      <div className={styles.segmented} role="group" aria-label="Filter by platform">
        {SOURCE_OPTIONS.map((option) => (
          <button
            key={option.label}
            type="button"
            className={styles.segment}
            aria-pressed={source === option.value}
            onClick={() => onSourceChange(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

      <button
        type="button"
        role="switch"
        aria-checked={freeOnly}
        className={styles.switch}
        onClick={() => onFreeOnlyChange(!freeOnly)}
      >
        <span className={styles.track} aria-hidden="true">
          <span className={styles.thumb} />
        </span>
        Free only
      </button>
    </div>
  );
}
