-- Every user has one role. Existing users get USER through the default.
-- The check constraint keeps typos like 'admin' or 'SUPERUSER' out of the table.
ALTER TABLE app_user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN'));
