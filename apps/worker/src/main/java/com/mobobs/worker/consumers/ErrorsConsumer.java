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
 * Batch consumer for {@code mobile.errors.raw}.
 *
 * Handles: error → mobobs.mobile_errors.
 *
 * Failures propagate to {@code DefaultErrorHandler} which retries and routes
 * poison messages to {@code mobile.errors.dlq}.
 */
@Component
public class ErrorsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ErrorsConsumer.class);

    private final ClickHouseSink clickHouseSink;

    public ErrorsConsumer(ClickHouseSink clickHouseSink) {
        this.clickHouseSink = clickHouseSink;
    }

    @KafkaListener(
            topics = "mobile.errors.raw",
            groupId = "worker-errors-group",
            containerFactory = "batchListenerContainerFactory")
    public void consume(List<TelemetryEnvelope> envelopes, Acknowledgment ack) {
        log.info("Received batch of {} envelope(s) from mobile.errors.raw", envelopes.size());
        try {
            clickHouseSink.insertEvents(envelopes);
            ack.acknowledge();
            log.debug("Acknowledged batch of {} from mobile.errors.raw", envelopes.size());
        } catch (Exception ex) {
            log.error("Failed to insert batch of {} from mobile.errors.raw; will retry or DLQ",
                    envelopes.size(), ex);
            throw new RuntimeException("ClickHouse insert failed for mobile.errors.raw", ex);
        }
    }
}
