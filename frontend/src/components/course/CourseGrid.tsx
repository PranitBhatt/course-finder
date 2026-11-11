import type { ReactNode } from 'react';
import type { Course } from '../../api/types';
import { CourseCard } from './CourseCard';
import { CourseCardSkeleton } from './CourseCardSkeleton';
import styles from './CourseGrid.module.css';

function Grid({ children, busy }: { children: ReactNode; busy?: boolean }) {
  return (
    <ul className={styles.grid} aria-busy={busy || undefined}>
      {children}
    </ul>
  );
}

interface CourseGridProps {
  courses: Course[];
  /**
   * Optional extra control per card (e.g. "Track"). It sits on top of the card instead of inside
   * it, because the card is one big link and a link can't contain other interactive elements.
   */
  renderAction?: (course: Course) => ReactNode;
}

export function CourseGrid({ courses, renderAction }: CourseGridProps) {
  return (
    <Grid>
      {courses.map((course, index) => {
        const action = renderAction?.(course);
        return (
          <li key={course.id} className={styles.item} style={{ animationDelay: `${Math.min(index, 11) * 35}ms` }}>
            <CourseCard course={course} />
            {action && <div className={styles.action}>{action}</div>}
          </li>
        );
      })}
    </Grid>
  );
}

export function CourseGridSkeleton({ count }: { count: number }) {
  return (
    <Grid busy>
      {Array.from({ length: count }, (_, index) => (
        <li key={index}>
          <CourseCardSkeleton />
        </li>
      ))}
    </Grid>
  );
}
