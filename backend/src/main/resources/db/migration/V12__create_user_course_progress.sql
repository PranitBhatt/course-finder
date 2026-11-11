-- Courses a signed-in user plans to take, is taking, or has finished ("My Learning").
CREATE TABLE user_course_progress
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- The id clients see (e.g. to remove an entry whose course is gone). Random for the same reason
    -- as app_user.public_id: a counter would reveal how many entries exist and be guessable.
    public_id        UUID         NOT NULL DEFAULT gen_random_uuid(),
    -- Deleting an account deletes its learning history with it.
    user_id          BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Nullable + SET NULL on purpose: a full Coursera sync deletes courses that disappeared at the
    -- source. The user's history must not disappear with them, so only the link is cut.
    course_id        BIGINT       REFERENCES course (id) ON DELETE SET NULL,

    -- Snapshot of the course, refreshed on every create/update. Once course_id becomes NULL these
    -- are all we have left, so "My Learning" can still say what the entry was ("No longer available").
    -- No CHECK on course_source: it only records what the course was, and adding a platform
    -- should not need a second constraint change here.
    course_title     TEXT         NOT NULL,
    course_url       TEXT         NOT NULL,
    course_source    VARCHAR(32)  NOT NULL,

    status           VARCHAR(16)  NOT NULL DEFAULT 'PLANNED',
    progress_percent SMALLINT     NOT NULL DEFAULT 0,
    notes            VARCHAR(500),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_user_course_progress_public_id UNIQUE (public_id),
    -- One entry per user and course. Rows whose course was removed (course_id NULL) don't collide,
    -- because NULLs are never equal in a UNIQUE constraint.
    CONSTRAINT uq_user_course_progress_user_course UNIQUE (user_id, course_id),
    CONSTRAINT ck_user_course_progress_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_user_course_progress_percent CHECK (progress_percent BETWEEN 0 AND 100)
);

-- "My Learning" lists one user's entries, optionally filtered by status, newest change first.
CREATE INDEX idx_user_course_progress_user_status_updated
    ON user_course_progress (user_id, status, updated_at DESC);
