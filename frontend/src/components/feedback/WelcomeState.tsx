import { StatePanel, Suggestions } from './StatePanel';

export const POPULAR_TOPICS = ['machine learning', 'python', 'data science', 'web development', 'sql', 'ux design'];

/** Shown before the first search, when there is no ?q= in the URL. */
export function WelcomeState({ onPick }: { onPick: (topic: string) => void }) {
  return (
    <StatePanel label="Popular topics" labelTone="accent" title="Not sure where to start?">
      <p>Pick a topic, or type anything above. Results come from every platform at once.</p>
      <Suggestions topics={POPULAR_TOPICS} onPick={onPick} />
    </StatePanel>
  );
}
