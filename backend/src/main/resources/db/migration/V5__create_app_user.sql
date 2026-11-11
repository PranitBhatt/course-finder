-- Registered users. Named app_user because "user" is a reserved word in PostgreSQL.
CREATE TABLE app_user
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- UNIQUE here is the real guarantee against duplicate accounts; the check in the
    -- service is only there to return a friendly 409 instead of a database error.
    email        VARCHAR(255) NOT NULL UNIQUE,
    -- TODO: plain text for now, must be hashed before auth is enabled in prod.
    password     VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
