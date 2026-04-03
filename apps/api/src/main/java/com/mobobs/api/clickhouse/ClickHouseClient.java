package com.mobobs.api.clickhouse;

import com.mobobs.api.dashboard.Dtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin JDBC wrapper that executes read-only queries against ClickHouse.
 *
 * A new connection is obtained per query via DriverManager — acceptable for an
 * MVP dashboard with low query rates. Connection pooling can be layered on later.
 *
 * All SQL lives in {@link Queries}; this class only handles connection lifecycle
 * and ResultSet mapping.
 */
@Component
public class ClickHouseClient {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseClient.class);

    private final String jdbcUrl;

    public ClickHouseClient(@Value("${clickhouse.url}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    // ── Overview ──────────────────────────────────────────────────────────────

    public Dtos.OverviewResponse queryOverview(String app, String env, String release,
                                               OffsetDateTime from, OffsetDateTime to) {
        long totalEvents = 0, uniqueSessions = 0, totalErrors = 0, totalApiCalls = 0, apiErrors = 0;
        double avgAppStartMs = 0.0;

        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {

            try (PreparedStatement ps = conn.prepareStatement(Queries.OVERVIEW_EVENTS)) {
                bindFilters(ps, app, env, release, from, to, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalEvents    = rs.getLong("total_events");
                        uniqueSessions = rs.getLong("unique_sessions");
                        avgAppStartMs  = rs.getDouble("avg_app_start_ms");
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(Queries.OVERVIEW_ERRORS)) {
                bindFilters(ps, app, env, release, from, to, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) totalErrors = rs.getLong("total_errors");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(Queries.OVERVIEW_API)) {
                bindFilters(ps, app, env, release, from, to, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalApiCalls = rs.getLong("total_api_calls");
                        apiErrors     = rs.getLong("api_errors");
                    }
                }
            }

        } catch (SQLException e) {
            log.error("ClickHouse overview query failed", e);
            throw new RuntimeException("ClickHouse query failed", e);
        }

        return new Dtos.OverviewResponse(
                totalEvents, uniqueSessions, totalErrors,
                totalApiCalls, apiErrors, avgAppStartMs);
    }

    // ── Error feed ────────────────────────────────────────────────────────────

    public List<Dtos.ErrorRow> queryErrorFeed(String app, String env, String release,
                                              OffsetDateTime from, OffsetDateTime to,
                                              int limit) {
        List<Dtos.ErrorRow> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(Queries.ERROR_FEED)) {

            int idx = bindFilters(ps, app, env, release, from, to, 1);
            ps.setInt(idx, Math.min(limit, 500));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Dtos.ErrorRow(
                            rs.getString("event_id"),
                            rs.getString("error_class"),
                            rs.getString("error_message"),
                            rs.getString("release"),
                            rs.getString("platform"),
                            rs.getString("screen_name"),
                            toOffsetDateTime(rs.getTimestamp("event_time"))
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("ClickHouse error-feed query failed", e);
            throw new RuntimeException("ClickHouse query failed", e);
        }
        return rows;
    }

    // ── API latency ───────────────────────────────────────────────────────────

    public List<Dtos.LatencyBucket> queryApiLatency(String app, String env, String release,
                                                     String path, String method,
                                                     OffsetDateTime from, OffsetDateTime to) {
        List<Dtos.LatencyBucket> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = conn.prepareStatement(Queries.API_LATENCY)) {

            int idx = bindFilters(ps, app, env, release, from, to, 1);
            ps.setString(idx++, path   != null ? path   : "%");
            ps.setString(idx,   method != null ? method : "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Dtos.LatencyBucket(
                            rs.getString("path"),
                            rs.getString("method"),
                            rs.getString("release"),
                            toOffsetDateTime(rs.getTimestamp("bucket")),
                            rs.getLong("request_count"),
                            rs.getLong("error_count"),
                            rs.getDouble("p50_ms"),
                            rs.getDouble("p95_ms"),
                            rs.getDouble("p99_ms")
                    ));
                }
            }
        } catch (SQLException e) {
            log.error("ClickHouse api-latency query failed", e);
            throw new RuntimeException("ClickHouse query failed", e);
        }
        return rows;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Binds (app, env, release, from, to) starting at startIdx and returns the next index.
     * Null app/env/release are converted to "%" for LIKE-based wildcard matching.
     */
    private int bindFilters(PreparedStatement ps, String app, String env, String release,
                             OffsetDateTime from, OffsetDateTime to, int startIdx)
            throws SQLException {
        int i = startIdx;
        ps.setString(i++, app     != null ? app     : "%");
        ps.setString(i++, env     != null ? env     : "%");
        ps.setString(i++, release != null ? release : "%");
        ps.setTimestamp(i++, Timestamp.from(from.toInstant()));
        ps.setTimestamp(i++, Timestamp.from(to.toInstant()));
        return i;
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp ts) {
        if (ts == null) return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts.getTime()), ZoneOffset.UTC);
    }
}
