import { POPULAR_TOPICS } from './WelcomeState';
import { StatePanel, Suggestions } from './StatePanel';
import buttonStyles from './Button.module.css';

interface EmptyStateProps {
  query: string;
  hasFilters: boolean;
  onClearFilters: () => void;
  onPick: (topic: string) => void;
}

export function EmptyState({ query, hasFilters, onClearFilters, onPick }: EmptyStateProps) {
  return (
    <StatePanel label="0 results" role="status" title={<>Nothing found for “{query}”.</>}>
      {hasFilters ? (
        <>
          <p>Your filters might be hiding results.</p>
          <button type="button" className={buttonStyles.secondary} onClick={onClearFilters}>
            Clear filters
          </button>
        </>
      ) : (
        <p>Check the spelling, try a broader topic, or start from one of these:</p>
      )}
      <Suggestions topics={POPULAR_TOPICS.slice(0, 4)} onPick={onPick} />
    </StatePanel>
  );
}
