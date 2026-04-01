package com.mobobs.worker.telemetry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Worker-local copy of the telemetry DTOs.
 * Validation annotations are intentionally omitted — the worker only needs to
 * deserialize from Kafka; validation happens at the ingest boundary in the API.
 */
public final class TelemetryDtos {

    private TelemetryDtos() {}

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "event_type",
        visible = true
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = AppStartEvent.class,   name = "app_start"),
        @JsonSubTypes.Type(value = ScreenViewEvent.class, name = "screen_view"),
        @JsonSubTypes.Type(value = ApiTimingEvent.class,  name = "api_timing"),
        @JsonSubTypes.Type(value = ErrorEvent.class,      name = "error"),
        @JsonSubTypes.Type(value = CustomEvent.class,     name = "custom_event")
    })
    public sealed interface TelemetryEvent permits
            AppStartEvent, ScreenViewEvent, ApiTimingEvent, ErrorEvent, CustomEvent {

        @JsonProperty("event_id")    String eventId();
        @JsonProperty("event_type")  String eventType();
        @JsonProperty("session_id")  String sessionId();
        @JsonProperty("user_id")     String userId();
        @JsonProperty("app_name")    String appName();
                                     String environment();
        @JsonProperty("app_version") String appVersion();
                                     String release();
                                     String platform();
        @JsonProperty("device_model") String deviceModel();
        @JsonProperty("os_version")   String osVersion();
        @JsonProperty("trace_id")     String traceId();
                                      OffsetDateTime timestamp();
    }

    public record AppStartEvent(
        @JsonProperty("event_id")     String eventId,
        @JsonProperty("event_type")   String eventType,
        @JsonProperty("session_id")   String sessionId,
        @JsonProperty("user_id")      String userId,
        @JsonProperty("app_name")     String appName,
                                      String environment,
        @JsonProperty("app_version")  String appVersion,
                                      String release,
                                      String platform,
        @JsonProperty("device_model") String deviceModel,
        @JsonProperty("os_version")   String osVersion,
        @JsonProperty("trace_id")     String traceId,
                                      OffsetDateTime timestamp,
        @JsonProperty("cold_start")   Boolean coldStart,
        @JsonProperty("duration_ms")  Double durationMs
    ) implements TelemetryEvent {}

    public record ScreenViewEvent(
        @JsonProperty("event_id")     String eventId,
        @JsonProperty("event_type")   String eventType,
        @JsonProperty("session_id")   String sessionId,
        @JsonProperty("user_id")      String userId,
        @JsonProperty("app_name")     String appName,
                                      String environment,
        @JsonProperty("app_version")  String appVersion,
                                      String release,
                                      String platform,
        @JsonProperty("device_model") String deviceModel,
        @JsonProperty("os_version")   String osVersion,
        @JsonProperty("trace_id")     String traceId,
                                      OffsetDateTime timestamp,
        @JsonProperty("screen_name")  String screenName
    ) implements TelemetryEvent {}

    public record ApiTimingEvent(
        @JsonProperty("event_id")       String eventId,
        @JsonProperty("event_type")     String eventType,
        @JsonProperty("session_id")     String sessionId,
        @JsonProperty("user_id")        String userId,
        @JsonProperty("app_name")       String appName,
                                        String environment,
        @JsonProperty("app_version")    String appVersion,
                                        String release,
                                        String platform,
        @JsonProperty("device_model")   String deviceModel,
        @JsonProperty("os_version")     String osVersion,
        @JsonProperty("trace_id")       String traceId,
                                        OffsetDateTime timestamp,
                                        String method,
                                        String path,
        @JsonProperty("status_code")    int statusCode,
        @JsonProperty("duration_ms")    double durationMs,
        @JsonProperty("request_size")   Long requestSize,
        @JsonProperty("response_size")  Long responseSize,
        @JsonProperty("error_message")  String errorMessage
    ) implements TelemetryEvent {}

    public record ErrorEvent(
        @JsonProperty("event_id")       String eventId,
        @JsonProperty("event_type")     String eventType,
        @JsonProperty("session_id")     String sessionId,
        @JsonProperty("user_id")        String userId,
        @JsonProperty("app_name")       String appName,
                                        String environment,
        @JsonProperty("app_version")    String appVersion,
                                        String release,
                                        String platform,
        @JsonProperty("device_model")   String deviceModel,
        @JsonProperty("os_version")     String osVersion,
        @JsonProperty("trace_id")       String traceId,
                                        OffsetDateTime timestamp,
        @JsonProperty("error_type")     String errorType,
        @JsonProperty("error_class")    String errorClass,
        @JsonProperty("error_message")  String errorMessage,
                                        String stacktrace,
        @JsonProperty("screen_name")    String screenName
    ) implements TelemetryEvent {}

    public record CustomEvent(
        @JsonProperty("event_id")     String eventId,
        @JsonProperty("event_type")   String eventType,
        @JsonProperty("session_id")   String sessionId,
        @JsonProperty("user_id")      String userId,
        @JsonProperty("app_name")     String appName,
                                      String environment,
        @JsonProperty("app_version")  String appVersion,
                                      String release,
                                      String platform,
        @JsonProperty("device_model") String deviceModel,
        @JsonProperty("os_version")   String osVersion,
        @JsonProperty("trace_id")     String traceId,
                                      OffsetDateTime timestamp,
        @JsonProperty("event_name")   String eventName,
        @JsonProperty("event_data")   Map<String, Object> eventData
    ) implements TelemetryEvent {}
}
