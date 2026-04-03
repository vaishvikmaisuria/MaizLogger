package com.mobobs.api.dashboard;

import com.mobobs.api.clickhouse.ClickHouseClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Dashboard query endpoints — all read-only, unauthenticated for the MVP dashboard.
 *
 * Endpoint contract:
 *   GET /v1/metrics/overview  — overview totals (events, sessions, errors, API calls)
 *   GET /v1/errors/feed       — recent error feed, newest first
 *   GET /v1/api/latency       — API latency percentiles bucketed by hour
 *
 * All time parameters are ISO-8601 with timezone offset, e.g. "2026-01-01T00:00:00Z".
 * Omitting optional filter params returns data for all values of that dimension.
 */
@RestController
@RequestMapping("/v1")
public class DashboardController {

    private static final int DEFAULT_LOOK_BACK_HOURS = 24;
    private static final int DEFAULT_ERROR_LIMIT      = 50;

    private final ClickHouseClient clickHouse;

    public DashboardController(ClickHouseClient clickHouse) {
        this.clickHouse = clickHouse;
    }

    /**
     * GET /v1/metrics/overview
     *
     * Query params (all optional):
     *   app     — app name filter (exact match)
     *   env     — environment filter (exact match)
     *   release — release filter (exact match)
     *   from    — ISO-8601 start (default: 24 h ago)
     *   to      — ISO-8601 end   (default: now)
     */
    @GetMapping("/metrics/overview")
    public ResponseEntity<Dtos.OverviewResponse> overview(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String env,
            @RequestParam(required = false) String release,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        OffsetDateTime resolvedTo   = to   != null ? to   : OffsetDateTime.now();
        OffsetDateTime resolvedFrom = from != null ? from : resolvedTo.minusHours(DEFAULT_LOOK_BACK_HOURS);

        Dtos.OverviewResponse result =
                clickHouse.queryOverview(app, env, release, resolvedFrom, resolvedTo);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /v1/errors/feed
     *
     * Query params (all optional):
     *   app, env, release, from, to — same as /overview
     *   limit — max rows to return (default 50, max 500)
     */
    @GetMapping("/errors/feed")
    public ResponseEntity<Dtos.ErrorFeedResponse> errorFeed(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String env,
            @RequestParam(required = false) String release,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "50") int limit) {

        OffsetDateTime resolvedTo   = to   != null ? to   : OffsetDateTime.now();
        OffsetDateTime resolvedFrom = from != null ? from : resolvedTo.minusHours(DEFAULT_LOOK_BACK_HOURS);

        List<Dtos.ErrorRow> errors =
                clickHouse.queryErrorFeed(app, env, release, resolvedFrom, resolvedTo, limit);
        return ResponseEntity.ok(new Dtos.ErrorFeedResponse(errors.size(), errors));
    }

    /**
     * GET /v1/api/latency
     *
     * Query params (all optional):
     *   app, env, release, from, to — same as /overview
     *   path   — endpoint path filter (exact match, e.g. "/checkout")
     *   method — HTTP method filter (e.g. "POST")
     */
    @GetMapping("/api/latency")
    public ResponseEntity<Dtos.LatencyResponse> apiLatency(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String env,
            @RequestParam(required = false) String release,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String method) {

        OffsetDateTime resolvedTo   = to   != null ? to   : OffsetDateTime.now();
        OffsetDateTime resolvedFrom = from != null ? from : resolvedTo.minusHours(DEFAULT_LOOK_BACK_HOURS);

        List<Dtos.LatencyBucket> buckets =
                clickHouse.queryApiLatency(app, env, release, path, method, resolvedFrom, resolvedTo);
        return ResponseEntity.ok(new Dtos.LatencyResponse(buckets));
    }
}
