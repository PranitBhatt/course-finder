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

export function CourseGrid({ courses }: { courses: Course[] }) {
  return (
    <Grid>
      {courses.map((course, index) => (
        <li key={course.id} className={styles.item} style={{ animationDelay: `${Math.min(index, 11) * 35}ms` }}>
          <CourseCard course={course} />
        </li>
      ))}
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
