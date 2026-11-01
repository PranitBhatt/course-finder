import { useEffect, useState } from 'react';
import { fetchSources } from '../api/client';
import type { SourceInfo } from '../api/types';

/** Loads /api/sources once. Failures are ignored: the page works without it. */
export function useSources(): SourceInfo[] {
  const [sources, setSources] = useState<SourceInfo[]>([]);

  useEffect(() => {
    const controller = new AbortController();
    fetchSources(controller.signal)
      .then(setSources)
      .catch(() => undefined);
    return () => controller.abort();
  }, []);

  return sources;
}
