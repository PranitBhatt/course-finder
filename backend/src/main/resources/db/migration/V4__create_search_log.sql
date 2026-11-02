-- One row per search (first results page only, so paging through results isn't counted twice).
-- Used by GET /api/admin/stats to see what people look for and what content is missing.
CREATE TABLE search_log
(
    id           BIGSERIAL PRIMARY KEY,
    -- normalized: lowercase, trimmed, single spaces
    query        VARCHAR(200) NOT NULL,
    result_count INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_search_log_created_at ON search_log (created_at);
CREATE INDEX idx_search_log_query ON search_log (query);
