package com.mobobs.api.clickhouse;

/**
 * All ClickHouse SQL queries for the dashboard endpoints.
 *
 * Conventions:
 * - Parameters use '?' JDBC placeholders in order: app, env, release, from, to
 *   (then any extra parameters specific to the query).
 * - Wildcards: callers pass "%" when no filter should be applied.
 * - quantileTDigest is used for p50/p95/p99 — it is more memory-efficient
 *   than quantile() for high-cardinality datasets.
 * - Time buckets are truncated to 1-hour intervals for the latency trend chart.
 */
public final class Queries {

    private Queries() {}

    /**
     * Overview — events table: total events, unique sessions, avg app-start ms.
     * Parameters: app, env, release, from, to.
     */
    public static final String OVERVIEW_EVENTS = """
            SELECT
                count()                                        AS total_events,
                uniq(session_id)                              AS unique_sessions,
                avgIf(duration_ms, event_type = 'app_start') AS avg_app_start_ms
            FROM mobobs.mobile_events
            WHERE app LIKE ? AND env LIKE ? AND release LIKE ?
              AND event_time BETWEEN ? AND ?
            """;

    /**
     * Overview — errors table: total error count.
     * Parameters: app, env, release, from, to.
     */
    public static final String OVERVIEW_ERRORS = """
            SELECT count() AS total_errors
            FROM mobobs.mobile_errors
            WHERE app LIKE ? AND env LIKE ? AND release LIKE ?
              AND event_time BETWEEN ? AND ?
            """;

    /**
     * Overview — API calls table: total requests and error requests.
     * Parameters: app, env, release, from, to.
     */
    public static final String OVERVIEW_API = """
            SELECT
                count()                              AS total_api_calls,
                countIf(status_code >= 400)         AS api_errors
            FROM mobobs.mobile_api_calls
            WHERE app LIKE ? AND env LIKE ? AND release LIKE ?
              AND event_time BETWEEN ? AND ?
            """;

    /**
     * Recent error feed, ordered newest-first.
     * Parameters: app, env, release, from, to, limit.
     */
    public static final String ERROR_FEED = """
            SELECT
                event_id,
                error_class,
                error_message,
                release,
                platform,
                screen_name,
                event_time
            FROM mobobs.mobile_errors
            WHERE app LIKE ? AND env LIKE ? AND release LIKE ?
              AND event_time BETWEEN ? AND ?
            ORDER BY event_time DESC
            LIMIT ?
            """;

    /**
     * API latency percentiles bucketed by 1-hour intervals.
     * Parameters: app, env, release, from, to, path (LIKE), method (LIKE).
     *
     * quantileTDigest(level)(col) computes approximate percentiles using the
     * t-digest algorithm — more memory-efficient than exact quantile() for
     * large datasets.
     */
    public static final String API_LATENCY = """
            SELECT
                path,
                method,
                release,
                toStartOfHour(event_time)                           AS bucket,
                count()                                             AS request_count,
                countIf(status_code >= 400)                        AS error_count,
                quantileTDigest(0.50)(duration_ms)                 AS p50_ms,
                quantileTDigest(0.95)(duration_ms)                 AS p95_ms,
                quantileTDigest(0.99)(duration_ms)                 AS p99_ms
            FROM mobobs.mobile_api_calls
            WHERE app LIKE ? AND env LIKE ? AND release LIKE ?
              AND event_time BETWEEN ? AND ?
              AND path   LIKE ?
              AND method LIKE ?
            GROUP BY path, method, release, bucket
            ORDER BY bucket ASC, path ASC
            """;
}
