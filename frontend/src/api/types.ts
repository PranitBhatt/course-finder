// Mirrors the backend DTOs in com.coursefinder.dto.response.

export type CourseSource = 'COURSERA' | 'YOUTUBE';

export interface Course {
  id: number;
  source: CourseSource;
  sourceName: string;
  title: string;
  description: string | null;
  url: string;
  thumbnailUrl: string | null;
  provider: string | null;
  language: string | null;
  level: string | null;
  durationText: string | null;
  /** null = the platform doesn't say (Coursera) */
  free: boolean | null;
  rating: number | null;
  lastSyncedAt: string;
}

export interface CourseSearchResult extends Course {
  score: number;
}

export interface CourseSearchResponse {
  query: string;
  /** true when results come from typo-tolerant matching */
  fuzzyMatch: boolean;
  content: CourseSearchResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SourceInfo {
  source: CourseSource;
  displayName: string;
  syncMode: 'FULL_CATALOG' | 'ON_DEMAND';
  enabled: boolean;
  courseCount: number;
  lastSyncedAt: string | null;
  lastSyncStatus: 'RUNNING' | 'SUCCESS' | 'FAILED' | null;
  lastSyncFinishedAt: string | null;
}

/** RFC 7807 body returned by the backend for errors. */
export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}

export interface SearchParams {
  q: string;
  source: CourseSource | null;
  freeOnly: boolean;
  page: number;
}
