package com.mobobs.worker.consumers;

import com.mobobs.worker.clickhouse.ClickHouseSink;
import com.mobobs.worker.telemetry.TelemetryEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Batch consumer for {@code mobile.events.raw}.
 *
 * Handles: app_start, screen_view, custom_event → mobobs.mobile_events.
 *
 * At-least-once guarantee: offsets are committed only AFTER a successful
 * ClickHouse batch insert.  On failure the exception propagates to the
 * container's {@code DefaultErrorHandler}, which retries up to 5 times
 * (500 ms back-off) then routes the records to {@code mobile.events.dlq}.
 */
@Component
public class EventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventsConsumer.class);

    private final ClickHouseSink clickHouseSink;

    public EventsConsumer(ClickHouseSink clickHouseSink) {
        this.clickHouseSink = clickHouseSink;
    }

    @KafkaListener(
            topics = "mobile.events.raw",
            groupId = "worker-events-group",
            containerFactory = "batchListenerContainerFactory")
    public void consume(List<TelemetryEnvelope> envelopes, Acknowledgment ack) {
        log.info("Received batch of {} envelope(s) from mobile.events.raw", envelopes.size());
        try {
            clickHouseSink.insertEvents(envelopes);
            // Commit offsets only after a confirmed ClickHouse write
            ack.acknowledge();
            log.debug("Acknowledged batch of {} from mobile.events.raw", envelopes.size());
        } catch (Exception ex) {
            log.error("Failed to insert batch of {} from mobile.events.raw; will retry or DLQ",
                    envelopes.size(), ex);
            // Rethrow so DefaultErrorHandler can apply back-off / DLQ routing
            throw new RuntimeException("ClickHouse insert failed for mobile.events.raw", ex);
        }
    }
}
