-- Brute-force protection: count wrong passwords, lock the account for a while after too many.
ALTER TABLE app_user ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until TIMESTAMPTZ;

-- Public id: the id the outside world sees (API responses, JWT "sub"). The numeric id stays
-- internal (foreign keys, joins). gen_random_uuid() is built into PostgreSQL 13+, and the
-- DEFAULT fills in a fresh UUID for every existing row while the column is added.
ALTER TABLE app_user ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE app_user ADD CONSTRAINT uq_app_user_public_id UNIQUE (public_id);

-- DISABLED users keep their data but can't log in or refresh.
ALTER TABLE app_user ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'));

ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMPTZ;
