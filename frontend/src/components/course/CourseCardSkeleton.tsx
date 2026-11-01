import styles from './CourseCardSkeleton.module.css';

/** Same shape as CourseCard, shown while results load. */
export function CourseCardSkeleton() {
  return (
    <div className={styles.card} aria-hidden="true">
      <div className={`${styles.block} ${styles.thumb}`} />
      <div className={styles.body}>
        <div className={styles.row}>
          <div className={`${styles.block} ${styles.badge}`} />
          <div className={`${styles.block} ${styles.badge}`} />
        </div>
        <div className={`${styles.block} ${styles.line}`} />
        <div className={`${styles.block} ${styles.line} ${styles.short}`} />
        <div className={`${styles.block} ${styles.provider}`} />
      </div>
    </div>
  );
}
