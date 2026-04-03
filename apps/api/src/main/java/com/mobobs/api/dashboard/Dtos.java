package com.mobobs.api.dashboard;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTOs for the dashboard query endpoints.
 * All records are immutable and serialized by Jackson using default conventions.
 */
public final class Dtos {

    private Dtos() {}

    // ── GET /v1/metrics/overview ───────────────────────────────────────────

    /**
     * High-level summary for the chosen app / env / release / time window.
     *
     * @param totalEvents      Number of telemetry events received.
     * @param uniqueSessions   Approximate distinct sessions (HyperLogLog).
     * @param totalErrors      Total error events.
     * @param totalApiCalls    Total API timing events.
     * @param apiErrors        API calls with status_code >= 400.
     * @param avgAppStartMs    Average cold/warm start duration in milliseconds.
     */
    public record OverviewResponse(
            long   totalEvents,
            long   uniqueSessions,
            long   totalErrors,
            long   totalApiCalls,
            long   apiErrors,
            double avgAppStartMs
    ) {}

    // ── GET /v1/errors/feed ────────────────────────────────────────────────

    public record ErrorFeedResponse(
            int            count,
            List<ErrorRow> errors
    ) {}

    /**
     * One entry in the recent-errors feed.
     *
     * @param eventId      Unique event identifier.
     * @param errorClass   Fully-qualified exception class name.
     * @param errorMessage Human-readable error message.
     * @param release      App release (e.g. "v1.1.0").
     * @param platform     "ios" or "android".
     * @param screenName   Screen where the error occurred (nullable).
     * @param eventTime    When the error happened on device.
     */
    public record ErrorRow(
            String         eventId,
            String         errorClass,
            String         errorMessage,
            String         release,
            String         platform,
            String         screenName,
            OffsetDateTime eventTime
    ) {}

    // ── GET /v1/api/latency ────────────────────────────────────────────────

    public record LatencyResponse(
            List<LatencyBucket> buckets
    ) {}

    /**
     * One 1-hour bucket of API latency percentiles for a (path, method, release) combination.
     *
     * @param path          API endpoint path (e.g. "/checkout").
     * @param method        HTTP method (e.g. "POST").
     * @param release       App release.
     * @param bucket        Start of the 1-hour time bucket (UTC).
     * @param requestCount  Total requests in this bucket.
     * @param errorCount    Requests with status_code >= 400.
     * @param p50Ms         50th-percentile latency (t-digest approximate).
     * @param p95Ms         95th-percentile latency.
     * @param p99Ms         99th-percentile latency.
     */
    public record LatencyBucket(
            String         path,
            String         method,
            String         release,
            OffsetDateTime bucket,
            long           requestCount,
            long           errorCount,
            double         p50Ms,
            double         p95Ms,
            double         p99Ms
    ) {}
}
