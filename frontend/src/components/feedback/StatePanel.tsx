import type { ReactNode } from 'react';
import { MonoLabel } from '../ui/MonoLabel';
import styles from './StatePanel.module.css';

interface StatePanelProps {
  label: string;
  labelTone?: 'mute' | 'accent';
  title: ReactNode;
  children?: ReactNode;
  role?: 'alert' | 'status';
}

/** Shared white card for empty, error and welcome states. */
export function StatePanel({ label, labelTone = 'mute', title, children, role }: StatePanelProps) {
  return (
    <section className={styles.panel} role={role}>
      <MonoLabel tone={labelTone}>{label}</MonoLabel>
      <h2 className={styles.title}>{title}</h2>
      {children && <div className={styles.content}>{children}</div>}
    </section>
  );
}

interface SuggestionsProps {
  topics: string[];
  onPick: (topic: string) => void;
}

export function Suggestions({ topics, onPick }: SuggestionsProps) {
  return (
    <ul className={styles.suggestions}>
      {topics.map((topic) => (
        <li key={topic}>
          <button type="button" className={styles.chip} onClick={() => onPick(topic)}>
            {topic}
          </button>
        </li>
      ))}
    </ul>
  );
}
