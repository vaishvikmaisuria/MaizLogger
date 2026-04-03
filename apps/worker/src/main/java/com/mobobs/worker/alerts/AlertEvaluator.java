package com.mobobs.worker.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a single {@link AlertRule} against live ClickHouse data.
 *
 * <p>Supported metric types:
 * <ul>
 *   <li>{@code error_rate}      — errors per minute in the look-back window
 *                                 (queried from {@code mobobs.error_rate_1m})</li>
 *   <li>{@code p95_latency_ms}  — p95 request latency in milliseconds
 *                                 (queried from {@code mobobs.mobile_api_calls}
 *                                 using {@code quantileTDigest(0.95)})</li>
 *   <li>{@code failed_requests} — number of requests with HTTP status &ge; 400
 *                                 in the look-back window</li>
 * </ul>
 *
 * <p>The primary constructor is used by Spring; the secondary (package-private)
 * constructor exists for unit tests that inject a mock {@link Connection}.
 */
@Component
@ConditionalOnProperty(name = "mobobs.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class AlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);

    private final String chUrl;
    private final String chUser;
    private final String chPass;

    /** Primary constructor — called by Spring via {@code @Value} injection. */
    public AlertEvaluator(
            @Value("${clickhouse.url}") String chUrl,
            @Value("${clickhouse.username:default}") String chUser,
            @Value("${clickhouse.password:}") String chPass) {
        this.chUrl  = chUrl;
        this.chUser = chUser;
        this.chPass = chPass;
    }

    /**
     * Opens a single ClickHouse JDBC connection.
     * Package-private so unit tests can subclass / spy without touching DriverManager.
     */
    Connection openConnection() throws SQLException {
        return DriverManager.getConnection(chUrl, chUser, chPass);
    }

    /**
     * Evaluates the rule and returns the current observed metric value.
     *
     * @throws SQLException             if ClickHouse is unreachable
     * @throws IllegalArgumentException if the rule's {@code metricType} is unknown
     */
    public double evaluate(AlertRule rule) throws SQLException {
        try (Connection conn = openConnection()) {
            return evaluateWithConnection(rule, conn);
        }
    }

    /**
     * Package-private entry point used by unit tests to inject a prepared mock Connection,
     * bypassing DriverManager entirely.
     */
    double evaluateWithConnection(AlertRule rule, Connection conn) throws SQLException {
        return switch (rule.metricType()) {
            case "error_rate"      -> queryErrorRate(rule, conn);
            case "p95_latency_ms"  -> queryP95Latency(rule, conn);
            case "failed_requests" -> queryFailedRequests(rule, conn);
            default -> throw new IllegalArgumentException(
                    "Unknown metric type: " + rule.metricType());
        };
    }

    // ── error_rate: total errors / window_minutes ──────────────────────────

    private double queryErrorRate(AlertRule rule, Connection conn) throws SQLException {
        Instant windowStart = Instant.now().minus(rule.windowMinutes(), ChronoUnit.MINUTES);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT sum(error_count) FROM mobobs.error_rate_1m WHERE minute >= ?");
        params.add(Timestamp.from(windowStart));
        appendAppFilter(sql, params, rule);
        appendEnvFilter(sql, params, rule);

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double total = rs.getDouble(1);
                    return rule.windowMinutes() > 0 ? total / rule.windowMinutes() : total;
                }
            }
        }
        return 0.0;
    }

    // ── p95_latency_ms: quantileTDigest(0.95) from raw api calls ──────────

    private double queryP95Latency(AlertRule rule, Connection conn) throws SQLException {
        Instant windowStart = Instant.now().minus(rule.windowMinutes(), ChronoUnit.MINUTES);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT quantileTDigest(0.95)(duration_ms) FROM mobobs.mobile_api_calls WHERE event_time >= ?");
        params.add(Timestamp.from(windowStart));
        appendAppFilter(sql, params, rule);
        appendEnvFilter(sql, params, rule);

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    // ── failed_requests: count(status_code >= 400) ────────────────────────

    private double queryFailedRequests(AlertRule rule, Connection conn) throws SQLException {
        Instant windowStart = Instant.now().minus(rule.windowMinutes(), ChronoUnit.MINUTES);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT count() FROM mobobs.mobile_api_calls WHERE status_code >= 400 AND event_time >= ?");
        params.add(Timestamp.from(windowStart));
        appendAppFilter(sql, params, rule);
        appendEnvFilter(sql, params, rule);

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        }
        return 0.0;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void appendAppFilter(StringBuilder sql, List<Object> params, AlertRule rule) {
        if (rule.appName() != null && !rule.appName().isBlank()) {
            sql.append(" AND app = ?");
            params.add(rule.appName());
        }
    }

    private void appendEnvFilter(StringBuilder sql, List<Object> params, AlertRule rule) {
        if (rule.env() != null && !rule.env().isBlank()) {
            sql.append(" AND env = ?");
            params.add(rule.env());
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }
}
