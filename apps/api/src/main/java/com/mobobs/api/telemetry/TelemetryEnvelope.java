package com.mobobs.api.telemetry;

import com.mobobs.api.telemetry.TelemetryDtos.TelemetryEvent;
import java.time.OffsetDateTime;

public record TelemetryEnvelope(
    int schema_version,
    OffsetDateTime received_at,
    String source_ip,
    long app_id,
    TelemetryEvent event
) {}
