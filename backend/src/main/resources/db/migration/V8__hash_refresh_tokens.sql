-- Store only a SHA-256 hash of each refresh token instead of the token itself.

-- Existing tokens are stored raw and we can't tell which hash goes with which client,
-- so they're simply removed. That only means everyone logs in once more; refresh tokens
-- are short-lived credentials, not user data, so nothing of value is lost.
DELETE FROM refresh_token;

-- Dropping the column also drops its UNIQUE constraint.
ALTER TABLE refresh_token DROP COLUMN token;

-- SHA-256 = 32 bytes = always exactly 64 hex characters, hence CHAR(64).
ALTER TABLE refresh_token ADD COLUMN token_hash CHAR(64) NOT NULL;
ALTER TABLE refresh_token ADD CONSTRAINT uq_refresh_token_token_hash UNIQUE (token_hash);
