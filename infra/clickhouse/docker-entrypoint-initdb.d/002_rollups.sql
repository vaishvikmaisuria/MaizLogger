-- 002_rollups.sql — Materialized View rollups for Mobile Observability Platform
-- These shift computation from query time to insert time.

-- ─── Events Throughput Rollup (per minute) ────────────────────────────────────
CREATE TABLE IF NOT EXISTS mobobs.events_throughput_1m
(
    app               LowCardinality(String),
    env               LowCardinality(String),
    release           LowCardinality(String),
    platform          LowCardinality(String),
    event_type        LowCardinality(String),
    minute            DateTime,
    event_count       UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(minute)
ORDER BY (app, env, release, platform, event_type, minute);

CREATE MATERIALIZED VIEW IF NOT EXISTS mobobs.events_throughput_1m_mv
TO mobobs.events_throughput_1m
AS
SELECT
    app,
    env,
    release,
    platform,
    event_type,
    toStartOfMinute(event_time) AS minute,
    count() AS event_count
FROM mobobs.mobile_events
GROUP BY app, env, release, platform, event_type, minute;

-- ─── API Latency Rollup (per minute, per endpoint) ───────────────────────────
CREATE TABLE IF NOT EXISTS mobobs.api_latency_1m
(
    app               LowCardinality(String),
    env               LowCardinality(String),
    release           LowCardinality(String),
    path              String,
    method            LowCardinality(String),
    minute            DateTime,
    request_count     UInt64,
    error_count       UInt64,
    duration_sum      Float64,
    duration_max      Float64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(minute)
ORDER BY (app, env, release, path, method, minute);

CREATE MATERIALIZED VIEW IF NOT EXISTS mobobs.api_latency_1m_mv
TO mobobs.api_latency_1m
AS
SELECT
    app,
    env,
    release,
    path,
    method,
    toStartOfMinute(event_time) AS minute,
    count() AS request_count,
    countIf(status_code >= 400) AS error_count,
    sum(duration_ms) AS duration_sum,
    max(duration_ms) AS duration_max
FROM mobobs.mobile_api_calls
GROUP BY app, env, release, path, method, minute;

-- ─── Error Rate Rollup (per minute) ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS mobobs.error_rate_1m
(
    app               LowCardinality(String),
    env               LowCardinality(String),
    release           LowCardinality(String),
    platform          LowCardinality(String),
    error_type        LowCardinality(String),
    error_class       String,
    minute            DateTime,
    error_count       UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(minute)
ORDER BY (app, env, release, platform, error_type, error_class, minute);

CREATE MATERIALIZED VIEW IF NOT EXISTS mobobs.error_rate_1m_mv
TO mobobs.error_rate_1m
AS
SELECT
    app,
    env,
    release,
    platform,
    error_type,
    error_class,
    toStartOfMinute(event_time) AS minute,
    count() AS error_count
FROM mobobs.mobile_errors
GROUP BY app, env, release, platform, error_type, error_class, minute;
