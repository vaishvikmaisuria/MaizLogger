package com.mobobs.worker.clickhouse;

import com.mobobs.worker.telemetry.TelemetryDtos.*;
import com.mobobs.worker.telemetry.TelemetryEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch-inserts telemetry envelopes into the appropriate ClickHouse table.
 *
 * Each call to {@link #insertEvents(List)} opens one JDBC connection per target
 * table, uses addBatch/executeBatch for efficiency, then closes the connection.
 * This is intentionally simple and stateless; connection-pool tuning can be
 * added later if throughput demands it.
 *
 * Offsets must NOT be committed until this method returns without throwing.
 */
@Component
public class ClickHouseSink {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseSink.class);

    // Column order must stay in sync with EventMapper binding methods.
    static final String INSERT_MOBILE_EVENTS =
            "INSERT INTO mobobs.mobile_events " +
            "(event_id, event_type, session_id, user_id, app, env, app_version, release, platform, " +
            "device_model, os_version, screen_name, event_data, event_time, received_at, " +
            "trace_id, source_ip, schema_version) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    static final String INSERT_API_CALLS =
            "INSERT INTO mobobs.mobile_api_calls " +
            "(event_id, session_id, user_id, app, env, app_version, release, platform, " +
            "device_model, os_version, method, path, status_code, duration_ms, " +
            "request_size, response_size, error_message, event_time, received_at, " +
            "trace_id, source_ip, schema_version) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    static final String INSERT_ERRORS =
            "INSERT INTO mobobs.mobile_errors " +
            "(event_id, session_id, user_id, app, env, app_version, release, platform, " +
            "device_model, os_version, error_type, error_class, error_message, stacktrace, " +
            "screen_name, event_time, received_at, trace_id, source_ip, schema_version) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final String url;
    private final String username;
    private final String password;

    public ClickHouseSink(
            @Value("${clickhouse.url}") String url,
            @Value("${clickhouse.username:default}") String username,
            @Value("${clickhouse.password:}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /**
     * Routes each envelope to the correct ClickHouse table and performs a
     * batch insert per table in a single JDBC roundtrip.
     *
     * @throws SQLException on any database error — callers should NOT ack the
     *                      Kafka offset when this throws.
     */
    public void insertEvents(List<TelemetryEnvelope> envelopes) throws SQLException {
        List<TelemetryEnvelope> mobileEvents = new ArrayList<>();
        List<TelemetryEnvelope> apiCalls     = new ArrayList<>();
        List<TelemetryEnvelope> errors       = new ArrayList<>();

        for (TelemetryEnvelope env : envelopes) {
            TelemetryEvent ev = env.event();
            if (ev instanceof AppStartEvent || ev instanceof ScreenViewEvent || ev instanceof CustomEvent) {
                mobileEvents.add(env);
            } else if (ev instanceof ApiTimingEvent) {
                apiCalls.add(env);
            } else if (ev instanceof ErrorEvent) {
                errors.add(env);
            } else {
                log.warn("Unknown event type '{}' for event_id='{}'; skipping",
                        ev.eventType(), ev.eventId());
            }
        }

        if (!mobileEvents.isEmpty()) insertMobileEvents(mobileEvents);
        if (!apiCalls.isEmpty())     insertApiCalls(apiCalls);
        if (!errors.isEmpty())       insertErrors(errors);
    }

    private void insertMobileEvents(List<TelemetryEnvelope> envelopes) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_MOBILE_EVENTS)) {
            for (TelemetryEnvelope env : envelopes) {
                EventMapper.bindMobileEvent(ps, env);
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Inserted {} row(s) into mobile_events", envelopes.size());
        }
    }

    private void insertApiCalls(List<TelemetryEnvelope> envelopes) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_API_CALLS)) {
            for (TelemetryEnvelope env : envelopes) {
                EventMapper.bindApiCall(ps, env);
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Inserted {} row(s) into mobile_api_calls", envelopes.size());
        }
    }

    private void insertErrors(List<TelemetryEnvelope> envelopes) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_ERRORS)) {
            for (TelemetryEnvelope env : envelopes) {
                EventMapper.bindError(ps, env);
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Inserted {} row(s) into mobile_errors", envelopes.size());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
