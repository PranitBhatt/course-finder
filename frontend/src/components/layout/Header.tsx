import { Link } from 'react-router';
import type { Theme } from '../../hooks/useTheme';
import { ThemeToggle } from '../ui/ThemeToggle';
import styles from './Header.module.css';

interface HeaderProps {
  theme: Theme;
  onToggleTheme: () => void;
}

export function Header({ theme, onToggleTheme }: HeaderProps) {
  return (
    <header className={styles.header}>
      <Link to="/" className={styles.brand} aria-label="Course Finder home">
        <span className={styles.mark} aria-hidden="true">
          <svg viewBox="0 0 24 24" width="18" height="18">
            <circle cx="10.5" cy="10.5" r="5.5" fill="none" stroke="currentColor" strokeWidth="2.6" />
            <path d="M15 15l4.5 4.5" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" />
          </svg>
        </span>
        <span className={styles.name}>Course Finder</span>
      </Link>
      <ThemeToggle theme={theme} onToggle={onToggleTheme} />
    </header>
  );
}
