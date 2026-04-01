package com.mobobs.worker.telemetry;

import com.mobobs.worker.telemetry.TelemetryDtos.TelemetryEvent;
import java.time.OffsetDateTime;

/**
 * Wrapper produced by the API and published to Kafka raw topics.
 * The worker deserialises this from Kafka as the top-level value type.
 */
public record TelemetryEnvelope(
    int schema_version,
    OffsetDateTime received_at,
    String source_ip,
    long app_id,
    TelemetryEvent event
) {}
