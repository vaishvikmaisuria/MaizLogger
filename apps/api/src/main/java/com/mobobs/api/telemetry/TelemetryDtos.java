package com.mobobs.api.telemetry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class TelemetryDtos {

    private TelemetryDtos() {}

    public record TelemetryBatch(
        @NotEmpty(message = "events must not be empty")
        @Valid
        List<TelemetryEvent> events
    ) {}

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "event_type",
        visible = true
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = AppStartEvent.class, name = "app_start"),
        @JsonSubTypes.Type(value = ScreenViewEvent.class, name = "screen_view"),
        @JsonSubTypes.Type(value = ApiTimingEvent.class, name = "api_timing"),
        @JsonSubTypes.Type(value = ErrorEvent.class, name = "error"),
        @JsonSubTypes.Type(value = CustomEvent.class, name = "custom_event")
    })
    public sealed interface TelemetryEvent permits
            AppStartEvent, ScreenViewEvent, ApiTimingEvent, ErrorEvent, CustomEvent {

        @JsonProperty("event_id")
        @NotBlank String eventId();

        @JsonProperty("event_type")
        @NotBlank String eventType();

        @JsonProperty("session_id")
        @NotBlank String sessionId();

        @JsonProperty("user_id")
        String userId();

        @JsonProperty("app_name")
        @NotBlank String appName();

        String environment();

        @JsonProperty("app_version")
        @NotBlank String appVersion();

        String release();

        @NotBlank String platform();

        @JsonProperty("device_model")
        String deviceModel();

        @JsonProperty("os_version")
        String osVersion();

        @NotNull OffsetDateTime timestamp();

        @JsonProperty("trace_id")
        String traceId();
    }

    public record AppStartEvent(
        @JsonProperty("event_id") @NotBlank String eventId,
        @JsonProperty("event_type") @NotBlank String eventType,
        @JsonProperty("session_id") @NotBlank String sessionId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("app_name") @NotBlank String appName,
        String environment,
        @JsonProperty("app_version") @NotBlank String appVersion,
        String release,
        @NotBlank String platform,
        @JsonProperty("device_model") String deviceModel,
        @JsonProperty("os_version") String osVersion,
        @NotNull OffsetDateTime timestamp,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("cold_start") Boolean coldStart,
        @JsonProperty("duration_ms") Double durationMs
    ) implements TelemetryEvent {}

    public record ScreenViewEvent(
        @JsonProperty("event_id") @NotBlank String eventId,
        @JsonProperty("event_type") @NotBlank String eventType,
        @JsonProperty("session_id") @NotBlank String sessionId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("app_name") @NotBlank String appName,
        String environment,
        @JsonProperty("app_version") @NotBlank String appVersion,
        String release,
        @NotBlank String platform,
        @JsonProperty("device_model") String deviceModel,
        @JsonProperty("os_version") String osVersion,
        @NotNull OffsetDateTime timestamp,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("screen_name") @NotBlank String screenName
    ) implements TelemetryEvent {}

    public record ApiTimingEvent(
        @JsonProperty("event_id") @NotBlank String eventId,
        @JsonProperty("event_type") @NotBlank String eventType,
        @JsonProperty("session_id") @NotBlank String sessionId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("app_name") @NotBlank String appName,
        String environment,
        @JsonProperty("app_version") @NotBlank String appVersion,
        String release,
        @NotBlank String platform,
        @JsonProperty("device_model") String deviceModel,
        @JsonProperty("os_version") String osVersion,
        @NotNull OffsetDateTime timestamp,
        @JsonProperty("trace_id") String traceId,
        @NotBlank String method,
        @NotBlank String path,
        @JsonProperty("status_code") int statusCode,
        @JsonProperty("duration_ms") double durationMs,
        @JsonProperty("request_size") Long requestSize,
        @JsonProperty("response_size") Long responseSize,
        @JsonProperty("error_message") String errorMessage
    ) implements TelemetryEvent {}

    public record ErrorEvent(
        @JsonProperty("event_id") @NotBlank String eventId,
        @JsonProperty("event_type") @NotBlank String eventType,
        @JsonProperty("session_id") @NotBlank String sessionId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("app_name") @NotBlank String appName,
        String environment,
        @JsonProperty("app_version") @NotBlank String appVersion,
        String release,
        @NotBlank String platform,
        @JsonProperty("device_model") String deviceModel,
        @JsonProperty("os_version") String osVersion,
        @NotNull OffsetDateTime timestamp,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("error_type") @NotBlank String errorType,
        @JsonProperty("error_class") @NotBlank String errorClass,
        @JsonProperty("error_message") @NotBlank String errorMessage,
        String stacktrace,
        @JsonProperty("screen_name") String screenName
    ) implements TelemetryEvent {}

    public record CustomEvent(
        @JsonProperty("event_id") @NotBlank String eventId,
        @JsonProperty("event_type") @NotBlank String eventType,
        @JsonProperty("session_id") @NotBlank String sessionId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("app_name") @NotBlank String appName,
        String environment,
        @JsonProperty("app_version") @NotBlank String appVersion,
        String release,
        @NotBlank String platform,
        @JsonProperty("device_model") String deviceModel,
        @JsonProperty("os_version") String osVersion,
        @NotNull OffsetDateTime timestamp,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("event_name") @NotBlank String eventName,
        @JsonProperty("event_data") Map<String, Object> eventData
    ) implements TelemetryEvent {}
}
