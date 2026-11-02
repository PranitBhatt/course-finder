export const SITE_NAME = 'Course Finder';

/** Must match the <title> in index.html. */
export const DEFAULT_TITLE = `${SITE_NAME} | Search Coursera and YouTube courses`;

/** "python" -> "Python courses | Course Finder"; "sql course" -> "Sql courses | Course Finder" */
export function searchTitle(query: string): string {
  const topic = query.trim().replace(/\s+courses?$/i, '');
  if (!topic) return DEFAULT_TITLE;
  return `${topic.charAt(0).toUpperCase()}${topic.slice(1)} courses | ${SITE_NAME}`;
}
