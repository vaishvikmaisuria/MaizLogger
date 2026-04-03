package com.mobobs.worker.alerts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlertEvaluator}.
 *
 * Each test injects a mock {@link Connection} via
 * {@link AlertEvaluator#evaluateWithConnection(AlertRule, Connection)},
 * bypassing DriverManager entirely — no real ClickHouse needed.
 */
class AlertEvaluatorTest {

    private Connection        conn;
    private PreparedStatement ps;
    private ResultSet         rs;

    private AlertEvaluator evaluator;

    @BeforeEach
    void setUp() throws Exception {
        conn = mock(Connection.class);
        ps   = mock(PreparedStatement.class);
        rs   = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);

        // Construct via the public constructor — Spring @Value parameters are
        // strings; openConnection() is never called by evaluateWithConnection().
        evaluator = new AlertEvaluator(
                "jdbc:clickhouse://localhost:8123/mobobs", "default", "");
    }

    private AlertRule rule(String metricType, double threshold) {
        return new AlertRule(1L, 1L, "demo-app", "Test Rule", metricType, threshold, 5, "prod", null);
    }

    // ── error_rate ────────────────────────────────────────────────────────

    @Test
    void errorRateDividesTotalErrorsByWindowMinutes() throws Exception {
        when(rs.next()).thenReturn(true);
        when(rs.getDouble(1)).thenReturn(50.0); // 50 total errors in 5-minute window

        double value = evaluator.evaluateWithConnection(rule("error_rate", 10.0), conn);

        assertThat(value).isEqualTo(10.0); // 50 / 5 = 10 errors/min
    }

    @Test
    void errorRateReturnsZeroWhenResultSetEmpty() throws Exception {
        when(rs.next()).thenReturn(false);

        double value = evaluator.evaluateWithConnection(rule("error_rate", 10.0), conn);

        assertThat(value).isZero();
    }

    // ── p95_latency_ms ────────────────────────────────────────────────────

    @Test
    void p95LatencyReturnsRawMillisecondsFromClickHouse() throws Exception {
        when(rs.next()).thenReturn(true);
        when(rs.getDouble(1)).thenReturn(1234.5);

        double value = evaluator.evaluateWithConnection(rule("p95_latency_ms", 2000.0), conn);

        assertThat(value).isEqualTo(1234.5);
    }

    // ── failed_requests ───────────────────────────────────────────────────

    @Test
    void failedRequestsReturnsCountFromClickHouse() throws Exception {
        when(rs.next()).thenReturn(true);
        when(rs.getDouble(1)).thenReturn(73.0);

        double value = evaluator.evaluateWithConnection(rule("failed_requests", 50.0), conn);

        assertThat(value).isEqualTo(73.0);
    }

    @Test
    void failedRequestsReturnsZeroWhenResultSetEmpty() throws Exception {
        when(rs.next()).thenReturn(false);

        double value = evaluator.evaluateWithConnection(rule("failed_requests", 50.0), conn);

        assertThat(value).isZero();
    }

    // ── unknown metric type ───────────────────────────────────────────────

    @Test
    void throwsIllegalArgumentForUnknownMetricType() {
        AlertRule bad = new AlertRule(1L, 1L, "demo", "bad", "unknown_type", 0.0, 5, null, null);

        assertThatThrownBy(() -> evaluator.evaluateWithConnection(bad, conn))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown_type");
    }

    // ── SQL construction ──────────────────────────────────────────────────

    @Test
    void appAndEnvFiltersAreIncludedInPreparedStatement() throws Exception {
        when(rs.next()).thenReturn(false);

        // Rule with both app and env filters
        AlertRule r = new AlertRule(1L, 1L, "demo-app", "r", "error_rate", 5.0, 5, "prod", null);
        evaluator.evaluateWithConnection(r, conn);

        // prepareStatement is called with SQL containing app + env clauses
        verify(conn).prepareStatement(argThat(sql ->
                sql.contains("app = ?") && sql.contains("env = ?")));
    }

    @Test
    void nullEnvDoesNotAddEnvFilter() throws Exception {
        when(rs.next()).thenReturn(false);

        AlertRule r = new AlertRule(1L, 1L, "demo-app", "r", "error_rate", 5.0, 5, null, null);
        evaluator.evaluateWithConnection(r, conn);

        verify(conn).prepareStatement(argThat(sql -> !sql.contains("env = ?")));
    }
}
