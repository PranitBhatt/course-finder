-- One row per catalog sync attempt (scheduled or manual).
CREATE TABLE sync_run
(
    id             BIGSERIAL PRIMARY KEY,
    source         VARCHAR(32) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL,
    finished_at    TIMESTAMPTZ,
    items_upserted INTEGER     NOT NULL DEFAULT 0,
    items_removed  INTEGER     NOT NULL DEFAULT 0,
    error_message  TEXT,

    CONSTRAINT ck_sync_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_sync_run_source_started ON sync_run (source, started_at DESC);

-- Cache of on-demand topic fetches (YouTube), so each topic hits the API at most once per TTL.
CREATE TABLE topic_fetch
(
    id           BIGSERIAL PRIMARY KEY,
    source       VARCHAR(32)  NOT NULL,
    topic        VARCHAR(200) NOT NULL,
    fetched_at   TIMESTAMPTZ  NOT NULL,
    result_count INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT uq_topic_fetch_source_topic UNIQUE (source, topic)
);
