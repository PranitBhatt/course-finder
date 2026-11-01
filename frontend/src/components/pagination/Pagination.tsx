import styles from './Pagination.module.css';

interface PaginationProps {
  /** zero-based */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

type PageItem = number | 'gap';

/** First, last, and the current page with one neighbour each side: 1 … 4 5 6 … 21 */
export function pageItems(page: number, totalPages: number): PageItem[] {
  const wanted = new Set([0, totalPages - 1, page - 1, page, page + 1]);
  const pages = [...wanted].filter((p) => p >= 0 && p < totalPages).sort((a, b) => a - b);

  const items: PageItem[] = [];
  pages.forEach((p, i) => {
    if (i > 0 && p - pages[i - 1] > 1) items.push('gap');
    items.push(p);
  });
  return items;
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  const isFirst = page === 0;
  const isLast = page >= totalPages - 1;

  return (
    <nav className={styles.pagination} aria-label="Search results pages">
      <button
        type="button"
        className={`${styles.button} ${styles.step}`}
        onClick={() => onPageChange(page - 1)}
        disabled={isFirst}
        aria-label="Previous page"
      >
        <Chevron direction="left" />
        <span className={styles.stepText}>Prev</span>
      </button>

      <ol className={styles.pages}>
        {pageItems(page, totalPages).map((item, index) =>
          item === 'gap' ? (
            <li key={`gap-${index}`} className={styles.gap} aria-hidden="true">
              …
            </li>
          ) : (
            <li key={item}>
              <button
                type="button"
                className={styles.button}
                onClick={() => onPageChange(item)}
                aria-current={item === page ? 'page' : undefined}
                aria-label={`Page ${item + 1}`}
              >
                {item + 1}
              </button>
            </li>
          ),
        )}
      </ol>

      <p className={styles.compact} aria-hidden="true">
        {page + 1} / {totalPages}
      </p>

      <button
        type="button"
        className={`${styles.button} ${styles.step}`}
        onClick={() => onPageChange(page + 1)}
        disabled={isLast}
        aria-label="Next page"
      >
        <span className={styles.stepText}>Next</span>
        <Chevron direction="right" />
      </button>
    </nav>
  );
}

function Chevron({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
      <path
        d={direction === 'left' ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'}
        fill="none"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
