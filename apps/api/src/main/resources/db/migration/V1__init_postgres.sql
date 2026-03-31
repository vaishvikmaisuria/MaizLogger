-- V1__init_postgres.sql — Initial Postgres schema for API metadata

CREATE TABLE IF NOT EXISTS apps (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS api_keys (
    id          BIGSERIAL PRIMARY KEY,
    app_id      BIGINT       NOT NULL REFERENCES apps(id),
    key_prefix  VARCHAR(8)   NOT NULL,
    key_hash    VARCHAR(255) NOT NULL,
    label       VARCHAR(255),
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix) WHERE active = true;
