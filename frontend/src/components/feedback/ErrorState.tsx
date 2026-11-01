import type { ApiError } from '../../api/client';
import { StatePanel } from './StatePanel';
import buttonStyles from './Button.module.css';

interface ErrorStateProps {
  error: ApiError;
  onRetry: () => void;
}

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  return (
    <StatePanel
      label={error.status ? `Error · ${error.status}` : 'Offline'}
      role="alert"
      title={error.isNetworkError ? "Can't reach the server." : 'Something went wrong.'}
    >
      <p>
        {error.isNetworkError
          ? 'The search service is not responding. Check that the backend is running on port 8080, then try again.'
          : error.message}
      </p>
      <button type="button" className={buttonStyles.primary} onClick={onRetry}>
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path
            d="M20 12a8 8 0 1 1-2.34-5.66M20 4v5h-5"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        Try again
      </button>
    </StatePanel>
  );
}
