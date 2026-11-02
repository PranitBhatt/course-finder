import type { ApiError } from '../../api/client';
import { StatePanel } from './StatePanel';
import buttonStyles from './Button.module.css';

interface ErrorStateProps {
  error: ApiError;
  onRetry: () => void;
}

// Local: point at the backend. Production: the free server may simply be asleep.
const UNREACHABLE_HINT = import.meta.env.DEV
  ? 'The search service is not responding. Check that the backend is running on port 8080, then try again.'
  : 'The search service is not responding. It may be starting up after a quiet period. Please try again in a minute.';

function describe(error: ApiError): { title: string; text: string } {
  if (error.isNetworkError) return { title: "Can't reach the server.", text: UNREACHABLE_HINT };
  if (error.isRateLimited) return { title: 'Slow down a little.', text: error.message };
  return { title: 'Something went wrong.', text: error.message };
}

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  const { title, text } = describe(error);

  return (
    <StatePanel label={error.status ? `Error · ${error.status}` : 'Offline'} role="alert" title={title}>
      <p>{text}</p>
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
