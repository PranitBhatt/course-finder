import { Link, useNavigate } from 'react-router';
import { AUTH_ENABLED } from '../../context/AuthContext';
import { useAuth } from '../../hooks/useAuth';
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
      <div className={styles.actions}>
        {AUTH_ENABLED && <AccountLinks />}
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />
      </div>
    </header>
  );
}

/** "Sign in" for guests, name + "Sign out" for logged-in users. Only rendered when auth is enabled. */
function AccountLinks() {
  const { user, loading, logout } = useAuth();
  const navigate = useNavigate();

  // While a saved token is being checked we don't know yet, so show nothing rather than
  // flashing "Sign in" at someone who is actually logged in.
  if (loading) return null;

  if (!user) {
    return (
      <Link to="/login" className={styles.link}>
        Sign in
      </Link>
    );
  }

  function handleSignOut() {
    logout();
    navigate('/');
  }

  return (
    <>
      <Link to="/account" className={styles.link}>
        {user.displayName}
      </Link>
      <button type="button" className={styles.signOut} onClick={handleSignOut}>
        Sign out
      </button>
    </>
  );
}
