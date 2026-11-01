package com.coursefinder.connector;

import java.util.List;
import java.util.function.Consumer;

/**
 * A platform whose whole catalog can be downloaded (e.g. Coursera).
 * Used by the scheduled full sync.
 */
public interface CatalogConnector extends CourseConnector {

    /**
     * Streams the full catalog page by page, so it never has to fit in memory.
     *
     * @param pageHandler called once per fetched page, in order
     * @return summary of what was fetched
     * @throws ConnectorException if a page can't be fetched after retries
     */
    CatalogFetchResult fetchCatalog(Consumer<List<ConnectorCourse>> pageHandler);

    /**
     * @param complete false if fetching stopped early (e.g. page cap reached), in which case
     *                 the caller must not treat missing courses as removed
     */
    record CatalogFetchResult(int pagesFetched, int coursesFetched, boolean complete) {
    }
}
