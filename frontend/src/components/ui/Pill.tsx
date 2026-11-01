import type { ReactNode } from 'react';
import styles from './Pill.module.css';

/** The reel's signature: a blue rounded block behind one key word of a headline. */
export function Pill({ children }: { children: ReactNode }) {
  return <span className={styles.pill}>{children}</span>;
}
