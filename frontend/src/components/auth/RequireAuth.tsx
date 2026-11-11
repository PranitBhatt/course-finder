import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { StatePanel } from '../feedback/StatePanel';

/**
 * Wrap a page that only makes sense for a logged-in user.
 * This only hides the page in the browser; the real protection is the backend
 * refusing requests without a valid token.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  // We have a saved token but /me hasn't answered yet. Redirecting now would send a
  // logged-in user to the login page for a split second, so wait.
  if (loading) {
    return (
      <main>
        <StatePanel label="Account" title="Checking your sign-in…" role="status" />
      </main>
    );
  }

  if (!user) {
    // Remember where they were going so the login page can send them back afterwards.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}
