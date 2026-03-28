-- 001_schema.sql — Core ClickHouse tables for Mobile Observability Platform
-- Database: mobobs

CREATE DATABASE IF NOT EXISTS mobobs;

-- ─── Mobile Events ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mobobs.mobile_events
(
    event_id          String,
    event_type        LowCardinality(String),  -- app_start, screen_view, custom_event
    session_id        String,
    user_id           Nullable(String),
    app               LowCardinality(String),
    env               LowCardinality(String),  -- production, staging, development
    app_version       LowCardinality(String),
    release           LowCardinality(String),
    platform          LowCardinality(String),  -- ios, android
    device_model      LowCardinality(String),
    os_version        LowCardinality(String),
    screen_name       Nullable(String),
    event_data        String DEFAULT '{}',      -- JSON payload for custom data
    event_time        DateTime64(3),
    received_at       DateTime64(3),
    trace_id          Nullable(String),
    source_ip         Nullable(String),
    schema_version    UInt8 DEFAULT 1
)
ENGINE = ReplacingMergeTree(received_at)
PARTITION BY toYYYYMM(event_time)
ORDER BY (app, env, event_type, event_time, event_id)
TTL toDateTime(event_time) + INTERVAL 90 DAY;

-- ─── Mobile API Calls ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mobobs.mobile_api_calls
(
    event_id          String,
    session_id        String,
    user_id           Nullable(String),
    app               LowCardinality(String),
    env               LowCardinality(String),
    app_version       LowCardinality(String),
    release           LowCardinality(String),
    platform          LowCardinality(String),
    device_model      LowCardinality(String),
    os_version        LowCardinality(String),
    method            LowCardinality(String),  -- GET, POST, PUT, DELETE, etc.
    path              String,
    status_code       UInt16,
    duration_ms       Float64,
    request_size      Nullable(UInt64),
    response_size     Nullable(UInt64),
    error_message     Nullable(String),
    event_time        DateTime64(3),
    received_at       DateTime64(3),
    trace_id          Nullable(String),
    source_ip         Nullable(String),
    schema_version    UInt8 DEFAULT 1
)
ENGINE = ReplacingMergeTree(received_at)
PARTITION BY toYYYYMM(event_time)
ORDER BY (app, env, path, method, event_time, event_id)
TTL toDateTime(event_time) + INTERVAL 90 DAY;

-- ─── Mobile Errors ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mobobs.mobile_errors
(
    event_id          String,
    session_id        String,
    user_id           Nullable(String),
    app               LowCardinality(String),
    env               LowCardinality(String),
    app_version       LowCardinality(String),
    release           LowCardinality(String),
    platform          LowCardinality(String),
    device_model      LowCardinality(String),
    os_version        LowCardinality(String),
    error_type        LowCardinality(String),  -- handled, unhandled
    error_class       String,
    error_message     String,
    stacktrace        Nullable(String),
    screen_name       Nullable(String),
    event_time        DateTime64(3),
    received_at       DateTime64(3),
    trace_id          Nullable(String),
    source_ip         Nullable(String),
    schema_version    UInt8 DEFAULT 1
)
ENGINE = ReplacingMergeTree(received_at)
PARTITION BY toYYYYMM(event_time)
ORDER BY (app, env, error_class, event_time, event_id)
TTL toDateTime(event_time) + INTERVAL 90 DAY;

-- ─── Mobile Sessions ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mobobs.mobile_sessions
(
    session_id        String,
    user_id           Nullable(String),
    app               LowCardinality(String),
    env               LowCardinality(String),
    app_version       LowCardinality(String),
    release           LowCardinality(String),
    platform          LowCardinality(String),
    device_model      LowCardinality(String),
    os_version        LowCardinality(String),
    started_at        DateTime64(3),
    ended_at          Nullable(DateTime64(3)),
    event_count       UInt32 DEFAULT 0,
    error_count       UInt32 DEFAULT 0,
    received_at       DateTime64(3),
    schema_version    UInt8 DEFAULT 1
)
ENGINE = ReplacingMergeTree(received_at)
PARTITION BY toYYYYMM(started_at)
ORDER BY (app, env, started_at, session_id)
TTL toDateTime(started_at) + INTERVAL 90 DAY;
