import type { ReactNode } from 'react';
import styles from './MonoLabel.module.css';

interface MonoLabelProps {
  children: ReactNode;
  tone?: 'mute' | 'accent' | 'faint';
  as?: 'p' | 'span' | 'h2';
  className?: string;
}

/** Small uppercase, letter-spaced JetBrains Mono label, e.g. "STEPS · WORST CASE" in the reel. */
export function MonoLabel({ children, tone = 'mute', as: Tag = 'p', className }: MonoLabelProps) {
  return <Tag className={[styles.label, styles[tone], className].filter(Boolean).join(' ')}>{children}</Tag>;
}
