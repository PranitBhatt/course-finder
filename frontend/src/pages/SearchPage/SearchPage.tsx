import { useEffect, useRef, useState } from 'react';
import type { CourseSource } from '../../api/types';
import { CourseGrid, CourseGridSkeleton } from '../../components/course/CourseGrid';
import { EmptyState } from '../../components/feedback/EmptyState';
import { ErrorState } from '../../components/feedback/ErrorState';
import { SlowLoadingNotice } from '../../components/feedback/SlowLoadingNotice';
import { WelcomeState } from '../../components/feedback/WelcomeState';
import { Pagination } from '../../components/pagination/Pagination';
import { TrackControl } from '../../components/progress/TrackControl';
import { Filters } from '../../components/search/Filters';
import { ResultsHeader } from '../../components/search/ResultsHeader';
import { SearchBar } from '../../components/search/SearchBar';
import { MonoLabel } from '../../components/ui/MonoLabel';
import { Pill } from '../../components/ui/Pill';
import { useCourseSearch } from '../../hooks/useCourseSearch';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { useProgressLookup } from '../../hooks/useProgressLookup';
import { useSearchUrlState } from '../../hooks/useSearchUrlState';
import { useSources } from '../../hooks/useSources';
import { formatNumber } from '../../utils/format';
import { DEFAULT_TITLE, searchTitle } from '../../utils/site';
import styles from './SearchPage.module.css';

const DEBOUNCE_MS = 350;
const SKELETON_COUNT = 6;

export function SearchPage() {
  const { params, update } = useSearchUrlState();
  const { state, retry } = useCourseSearch(params);
  const sources = useSources();
  const resultsRef = useRef<HTMLElement>(null);
  // Runs only once results are shown, so track badges never hold up the search itself.
  const courseIds = state.status === 'success' ? state.data.content.map((course) => course.id) : [];
  const progress = useProgressLookup(courseIds);

  // ---- search input <-> ?q= ----
  // `draft` is what's in the box; it reaches the URL after a pause in typing.
  const [draft, setDraft] = useState(params.q);
  const debouncedDraft = useDebouncedValue(draft, DEBOUNCE_MS);
  // The last q we wrote to the URL ourselves, so we can tell our updates from Back/Forward.
  const lastWrittenQuery = useRef(params.q);

  useEffect(() => {
    // Only once typing has settled; a stale value must not override e.g. a clicked suggestion.
    if (debouncedDraft !== draft) return;
    const q = debouncedDraft.trim();
    if (q !== lastWrittenQuery.current) {
      lastWrittenQuery.current = q;
      update({ q }, { replace: true });
    }
  }, [debouncedDraft, draft, update]);

  useEffect(() => {
    // The URL changed from outside the input (Back button, shared link): show it in the box.
    if (params.q !== lastWrittenQuery.current) {
      lastWrittenQuery.current = params.q;
      setDraft(params.q);
    }
  }, [params.q]);

  useEffect(() => {
    document.title = params.q ? searchTitle(params.q) : DEFAULT_TITLE;
  }, [params.q]);

  const searchNow = (value: string) => {
    const q = value.trim();
    setDraft(value);
    lastWrittenQuery.current = q;
    update({ q });
  };

  const changePage = (page: number) => {
    update({ page });
    resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const setSource = (source: CourseSource | null) => update({ source });
  const setFreeOnly = (freeOnly: boolean) => update({ freeOnly });
  const clearFilters = () => update({ source: null, freeOnly: false });

  const totalCourses = sources.reduce((sum, source) => sum + source.courseCount, 0);

  return (
    <main className={styles.page}>
      <section className={styles.hero}>
        <MonoLabel tone="accent">
          {totalCourses > 0 ? `Search · ${formatNumber(totalCourses)} courses` : 'Search · Coursera + YouTube'}
        </MonoLabel>
        <h1 className={styles.headline}>
          Find your next <Pill>course.</Pill>
        </h1>
        <p className={styles.lede}>Coursera and YouTube in one ranked list.</p>

        <div className={styles.controls}>
          <SearchBar
            value={draft}
            onChange={setDraft}
            onSubmit={searchNow}
            loading={state.status === 'loading'}
          />
          <Filters
            source={params.source}
            freeOnly={params.freeOnly}
            onSourceChange={setSource}
            onFreeOnlyChange={setFreeOnly}
          />
        </div>
      </section>

      <section ref={resultsRef} className={styles.results} aria-label="Search results">
        {state.status === 'idle' && <WelcomeState onPick={searchNow} />}

        {state.status === 'loading' && (
          <>
            <SlowLoadingNotice />
            <div className={styles.countSkeleton} aria-hidden="true" />
            <CourseGridSkeleton count={SKELETON_COUNT} />
          </>
        )}

        {state.status === 'error' && <ErrorState error={state.error} onRetry={retry} />}

        {state.status === 'success' && state.data.totalElements === 0 && (
          <EmptyState
            query={params.q}
            hasFilters={params.source !== null || params.freeOnly}
            onClearFilters={clearFilters}
            onPick={searchNow}
          />
        )}

        {state.status === 'success' && state.data.totalElements > 0 && (
          <>
            <ResultsHeader query={params.q} total={state.data.totalElements} fuzzyMatch={state.data.fuzzyMatch} />
            <CourseGrid
              courses={state.data.content}
              renderAction={(course) => (
                <TrackControl
                  courseTitle={course.title}
                  info={progress.statuses[course.id]}
                  loaded={progress.loaded}
                  error={progress.errors[course.id]}
                  onChange={(status) => progress.setStatus(course.id, status)}
                />
              )}
            />
            <Pagination page={state.data.page} totalPages={state.data.totalPages} onPageChange={changePage} />
          </>
        )}
      </section>
    </main>
  );
}
