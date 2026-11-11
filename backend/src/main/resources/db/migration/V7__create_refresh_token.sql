-- Long-lived refresh tokens, used to get new short-lived access tokens without logging in again.
-- Unlike access tokens (JWTs), these are stored, which is what makes logout possible.
CREATE TABLE refresh_token
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- ON DELETE CASCADE: deleting a user also removes all their refresh tokens,
    -- so a deleted account can't keep getting new access tokens.
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- The token exactly as given to the client (32 random bytes, Base64-URL = 43 chars).
    -- Stored as-is, so anyone who can read this table can use these tokens.
    token      VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- PostgreSQL doesn't index foreign keys automatically; without this, deleting a user
-- would scan the whole table to find their tokens.
CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
