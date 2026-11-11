-- Passwords are now stored as BCrypt hashes instead of plain text.

-- The only rows here so far are local test accounts, so it's fine to throw them away.
-- With real users we couldn't do this: we'd keep the rows and either hash the existing
-- passwords in a Java-based migration, or force everyone to reset their password.
DELETE FROM app_user;

ALTER TABLE app_user RENAME COLUMN password TO password_hash;

-- The app lowercases emails before saving. This makes the database enforce it too,
-- so "Ada@x.com" and "ada@x.com" can never end up as two separate accounts.
ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_email_lowercase CHECK (email = lower(email));
