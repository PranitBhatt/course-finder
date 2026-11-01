CREATE TABLE course
(
    id             BIGSERIAL PRIMARY KEY,
    source         VARCHAR(32)  NOT NULL,
    external_id    VARCHAR(255) NOT NULL,
    title          TEXT         NOT NULL,
    description    TEXT,
    url            TEXT         NOT NULL,
    thumbnail_url  TEXT,
    provider       TEXT,
    language       VARCHAR(16),
    level          VARCHAR(32),
    duration_text  VARCHAR(128),
    -- NULL = unknown (e.g. Coursera: audit is often free, certificate is paid)
    is_free        BOOLEAN,
    rating         NUMERIC(3, 2),
    last_synced_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Title matches (weight A) rank above description matches (weight B).
    search_vector  TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B')
    ) STORED,

    CONSTRAINT uq_course_source_external_id UNIQUE (source, external_id),
    CONSTRAINT ck_course_source CHECK (source IN ('COURSERA', 'YOUTUBE')),
    CONSTRAINT ck_course_rating CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5))
);

CREATE INDEX idx_course_search_vector ON course USING GIN (search_vector);
CREATE INDEX idx_course_title_trgm ON course USING GIN (title gin_trgm_ops);
CREATE INDEX idx_course_source_synced ON course (source, last_synced_at);
