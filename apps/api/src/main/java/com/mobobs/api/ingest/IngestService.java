package com.mobobs.api.ingest;

import com.mobobs.api.telemetry.TelemetryDtos.*;
import com.mobobs.api.telemetry.TelemetryEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private static final String TOPIC_EVENTS = "mobile.events.raw";
    private static final String TOPIC_API = "mobile.api.raw";
    private static final String TOPIC_ERRORS = "mobile.errors.raw";

    private static final int SCHEMA_VERSION = 1;

    private final KafkaTemplate<String, TelemetryEnvelope> kafkaTemplate;

    public IngestService(KafkaTemplate<String, TelemetryEnvelope> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public int processBatch(TelemetryBatch batch, long appId, String sourceIp) {
        OffsetDateTime receivedAt = OffsetDateTime.now();

        for (TelemetryEvent event : batch.events()) {
            TelemetryEnvelope envelope = new TelemetryEnvelope(
                SCHEMA_VERSION, receivedAt, sourceIp, appId, event
            );

            String topic = routeTopic(event);
            String key = event.sessionId();

            kafkaTemplate.send(topic, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {} to {}", event.eventId(), topic, ex);
                    } else {
                        log.debug("Published event {} to {}:{}", event.eventId(), topic,
                            result.getRecordMetadata().partition());
                    }
                });
        }

        return batch.events().size();
    }

    private String routeTopic(TelemetryEvent event) {
        if (event instanceof ApiTimingEvent) {
            return TOPIC_API;
        } else if (event instanceof ErrorEvent) {
            return TOPIC_ERRORS;
        } else {
            return TOPIC_EVENTS;
        }
    }
}
