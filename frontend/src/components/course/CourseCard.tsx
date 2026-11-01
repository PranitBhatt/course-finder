import { useState } from 'react';
import type { Course } from '../../api/types';
import { initials } from '../../utils/format';
import styles from './CourseCard.module.css';

interface CourseCardProps {
  course: Course;
}

/** The whole card is one link that opens the course on its platform in a new tab. */
export function CourseCard({ course }: CourseCardProps) {
  return (
    <a className={styles.card} href={course.url} target="_blank" rel="noopener noreferrer">
      <Thumbnail course={course} />

      <div className={styles.body}>
        <div className={styles.badges}>
          <SourceBadge course={course} />
          <PriceBadge free={course.free} />
        </div>

        <h3 className={styles.title}>{course.title}</h3>
        {course.provider && <p className={styles.provider}>{course.provider}</p>}

        <div className={styles.footer}>
          <span className={styles.duration}>{course.durationText ?? ' '}</span>
          <span className={styles.open} aria-hidden="true">
            Open
            <svg viewBox="0 0 24 24" width="14" height="14">
              <path d="M7 17L17 7M9 7h8v8" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
          <span className="visually-hidden">(opens {course.sourceName} in a new tab)</span>
        </div>
      </div>
    </a>
  );
}

function Thumbnail({ course }: CourseCardProps) {
  const [failed, setFailed] = useState(false);

  if (!course.thumbnailUrl || failed) {
    return (
      <div className={`${styles.thumb} ${styles.placeholder}`} aria-hidden="true">
        <span>{initials(course.title) || '?'}</span>
      </div>
    );
  }

  return (
    <div className={styles.thumb}>
      <img src={course.thumbnailUrl} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />
    </div>
  );
}

function SourceBadge({ course }: CourseCardProps) {
  return (
    <span className={styles.badge}>
      <span className={`${styles.dot} ${styles[course.source.toLowerCase()]}`} aria-hidden="true" />
      {course.sourceName}
    </span>
  );
}

/** Coursera doesn't tell us the price (audit is often free, certificates are paid), so null is shown honestly. */
function PriceBadge({ free }: { free: boolean | null }) {
  if (free === true) return <span className={`${styles.badge} ${styles.free}`}>Free</span>;
  if (free === false) return <span className={styles.badge}>Paid</span>;
  return <span className={`${styles.badge} ${styles.unknown}`}>Price varies</span>;
}
