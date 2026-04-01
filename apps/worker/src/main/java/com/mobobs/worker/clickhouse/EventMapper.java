package com.mobobs.worker.clickhouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobobs.worker.telemetry.TelemetryDtos.*;
import com.mobobs.worker.telemetry.TelemetryEnvelope;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Converts a {@link TelemetryEnvelope} into prepared-statement parameter bindings
 * for each ClickHouse target table.
 *
 * Kept in a separate class so it can be unit-tested without a real database.
 */
public final class EventMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EventMapper() {}

    // ── mobile_events (app_start, screen_view, custom_event) ─────────────────

    /**
     * Binds parameters for an INSERT into mobobs.mobile_events.
     * Column order must match the SQL in {@link ClickHouseSink#INSERT_MOBILE_EVENTS}.
     */
    public static void bindMobileEvent(PreparedStatement ps, TelemetryEnvelope env) throws SQLException {
        TelemetryEvent event = env.event();

        String screenName = (event instanceof ScreenViewEvent e) ? e.screenName() : null;
        String eventData  = "{}";
        if (event instanceof CustomEvent e && e.eventData() != null) {
            eventData = toJson(e.eventData());
        }

        ps.setString(1,  event.eventId());
        ps.setString(2,  event.eventType());
        ps.setString(3,  event.sessionId());
        ps.setString(4,  event.userId());           // nullable
        ps.setString(5,  event.appName());
        ps.setString(6,  event.environment());
        ps.setString(7,  event.appVersion());
        ps.setString(8,  event.release());
        ps.setString(9,  event.platform());
        ps.setString(10, event.deviceModel());
        ps.setString(11, event.osVersion());
        ps.setString(12, screenName);               // nullable
        ps.setString(13, eventData);
        ps.setObject(14, toTimestamp(event.timestamp()));
        ps.setObject(15, toTimestamp(env.received_at()));
        ps.setString(16, event.traceId());          // nullable
        ps.setString(17, env.source_ip());          // nullable
        ps.setInt(18,    env.schema_version());
    }

    // ── mobile_api_calls (api_timing) ────────────────────────────────────────

    /**
     * Binds parameters for an INSERT into mobobs.mobile_api_calls.
     * Column order must match the SQL in {@link ClickHouseSink#INSERT_API_CALLS}.
     */
    public static void bindApiCall(PreparedStatement ps, TelemetryEnvelope env) throws SQLException {
        ApiTimingEvent event = (ApiTimingEvent) env.event();

        ps.setString(1,  event.eventId());
        ps.setString(2,  event.sessionId());
        ps.setString(3,  event.userId());           // nullable
        ps.setString(4,  event.appName());
        ps.setString(5,  event.environment());
        ps.setString(6,  event.appVersion());
        ps.setString(7,  event.release());
        ps.setString(8,  event.platform());
        ps.setString(9,  event.deviceModel());
        ps.setString(10, event.osVersion());
        ps.setString(11, event.method());
        ps.setString(12, event.path());
        ps.setInt(13,    event.statusCode());
        ps.setDouble(14, event.durationMs());
        if (event.requestSize() != null) {
            ps.setLong(15, event.requestSize());
        } else {
            ps.setNull(15, java.sql.Types.BIGINT);
        }
        if (event.responseSize() != null) {
            ps.setLong(16, event.responseSize());
        } else {
            ps.setNull(16, java.sql.Types.BIGINT);
        }
        ps.setString(17, event.errorMessage());     // nullable
        ps.setObject(18, toTimestamp(event.timestamp()));
        ps.setObject(19, toTimestamp(env.received_at()));
        ps.setString(20, event.traceId());          // nullable
        ps.setString(21, env.source_ip());          // nullable
        ps.setInt(22,    env.schema_version());
    }

    // ── mobile_errors (error) ────────────────────────────────────────────────

    /**
     * Binds parameters for an INSERT into mobobs.mobile_errors.
     * Column order must match the SQL in {@link ClickHouseSink#INSERT_ERRORS}.
     */
    public static void bindError(PreparedStatement ps, TelemetryEnvelope env) throws SQLException {
        ErrorEvent event = (ErrorEvent) env.event();

        ps.setString(1,  event.eventId());
        ps.setString(2,  event.sessionId());
        ps.setString(3,  event.userId());           // nullable
        ps.setString(4,  event.appName());
        ps.setString(5,  event.environment());
        ps.setString(6,  event.appVersion());
        ps.setString(7,  event.release());
        ps.setString(8,  event.platform());
        ps.setString(9,  event.deviceModel());
        ps.setString(10, event.osVersion());
        ps.setString(11, event.errorType());
        ps.setString(12, event.errorClass());
        ps.setString(13, event.errorMessage());
        ps.setString(14, event.stacktrace());       // nullable
        ps.setString(15, event.screenName());       // nullable
        ps.setObject(16, toTimestamp(event.timestamp()));
        ps.setObject(17, toTimestamp(env.received_at()));
        ps.setString(18, event.traceId());          // nullable
        ps.setString(19, env.source_ip());          // nullable
        ps.setInt(20,    env.schema_version());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Timestamp toTimestamp(OffsetDateTime odt) {
        return odt != null ? Timestamp.from(odt.toInstant()) : null;
    }

    private static String toJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
