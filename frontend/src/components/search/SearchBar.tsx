import { useRef, type FormEvent } from 'react';
import styles from './SearchBar.module.css';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  /** Enter key: search right away instead of waiting for the debounce. */
  onSubmit: (value: string) => void;
  loading: boolean;
}

export function SearchBar({ value, onChange, onSubmit, loading }: SearchBarProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit(value);
  };

  const clear = () => {
    onChange('');
    inputRef.current?.focus();
  };

  return (
    <form role="search" className={styles.bar} onSubmit={handleSubmit}>
      <label htmlFor="course-search" className="visually-hidden">
        Search courses
      </label>
      <svg className={styles.icon} viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
        <circle cx="10.5" cy="10.5" r="6.5" fill="none" stroke="currentColor" strokeWidth="2.2" />
        <path d="M15.5 15.5l5 5" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
      </svg>
      <input
        ref={inputRef}
        id="course-search"
        className={styles.input}
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="Try “machine learning” or “python”"
        autoComplete="off"
        spellCheck={false}
        maxLength={200}
        enterKeyHint="search"
      />
      {loading && <span className={styles.spinner} role="status" aria-label="Searching" />}
      {value && !loading && (
        <button type="button" className={styles.clear} onClick={clear} aria-label="Clear search">
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
          </svg>
        </button>
      )}
    </form>
  );
}
