import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { ApiError } from '../../api/client';
import buttonStyles from '../../components/feedback/Button.module.css';
import styles from '../../components/auth/AuthForm.module.css';
import { useAuth } from '../../hooks/useAuth';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // RequireAuth puts the page the user wanted in `state.from`; "Sign in to track" links pass
  // ?next= instead, so the user lands back on their search results. Fall back to the home page.
  const from =
    (location.state as { from?: string } | null)?.from ??
    safeNext(new URLSearchParams(location.search).get('next')) ??
    '/';

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      // ApiError already holds the message from the backend's ProblemDetail
      // (e.g. "Invalid email or password"), so we can show it as-is.
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.');
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <form className={styles.card} onSubmit={handleSubmit}>
        <h1 className={styles.title}>Sign in</h1>

        <label className={styles.field}>
          Email
          <input
            className={styles.input}
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>

        <label className={styles.field}>
          Password
          <input
            className={styles.input}
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>

        {error && (
          <p className={styles.error} role="alert">
            {error}
          </p>
        )}

        <button type="submit" className={buttonStyles.primary} disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <p className={styles.footer}>
          No account yet? <Link to="/register">Create one</Link>
        </p>
      </form>
    </main>
  );
}

/**
 * Only same-site paths are allowed. Otherwise a link like /login?next=https://evil.example would
 * send the user to another site right after they signed in (an "open redirect").
 * "//evil.example" (and "/\evil.example", which browsers read the same way) are full addresses
 * too, so they're refused as well.
 */
function safeNext(next: string | null): string | null {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return null;
  return next;
}
