-- A session = one login on one device. Refresh tokens now belong to a session and are rotated:
-- every refresh uses up the old token and creates a new one in the same session.

-- Existing refresh tokens belong directly to a user, and there is no session we could attach them to.
-- Deleting them only means everyone logs in once more; no user data is lost.
DELETE FROM refresh_token;

CREATE TABLE user_session
(
    -- Random UUID instead of a counter: it goes into access tokens ("sid"), and a counter
    -- would tell anyone how many sessions exist and make ids guessable.
    id                  UUID PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ  NOT NULL,
    -- Hard limit: refreshing can never keep a session alive past this point.
    absolute_expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revoke_reason       VARCHAR(32),
    -- Shown to the user later ("Chrome on Windows, last used ..."). Not used for security decisions.
    user_agent          VARCHAR(512),
    -- 45 characters fits the longest IPv6 address.
    ip_address          VARCHAR(45),

    CONSTRAINT ck_user_session_revoke_reason CHECK (revoke_reason IN ('LOGOUT', 'REUSE_DETECTED'))
);

CREATE INDEX idx_user_session_user_id ON user_session (user_id);

-- Tokens now point to their session instead of their user (the user is reachable through the session).
-- Dropping user_id also drops idx_refresh_token_user_id.
ALTER TABLE refresh_token DROP COLUMN user_id;
ALTER TABLE refresh_token
    ADD COLUMN session_id UUID NOT NULL REFERENCES user_session (id) ON DELETE CASCADE;
-- Set when the token is exchanged for a new one. A second use of the same token means it was copied.
ALTER TABLE refresh_token ADD COLUMN used_at TIMESTAMPTZ;

CREATE INDEX idx_refresh_token_session_id ON refresh_token (session_id);
