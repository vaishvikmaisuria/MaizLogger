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
 * Batch consumer for {@code mobile.api.raw}.
 *
 * Handles: api_timing → mobobs.mobile_api_calls.
 *
 * Failures propagate to {@code DefaultErrorHandler} which retries and routes
 * poison messages to {@code mobile.api.dlq}.
 */
@Component
public class ApiConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApiConsumer.class);

    private final ClickHouseSink clickHouseSink;

    public ApiConsumer(ClickHouseSink clickHouseSink) {
        this.clickHouseSink = clickHouseSink;
    }

    @KafkaListener(
            topics = "mobile.api.raw",
            groupId = "worker-api-group",
            containerFactory = "batchListenerContainerFactory")
    public void consume(List<TelemetryEnvelope> envelopes, Acknowledgment ack) {
        log.info("Received batch of {} envelope(s) from mobile.api.raw", envelopes.size());
        try {
            clickHouseSink.insertEvents(envelopes);
            ack.acknowledge();
            log.debug("Acknowledged batch of {} from mobile.api.raw", envelopes.size());
        } catch (Exception ex) {
            log.error("Failed to insert batch of {} from mobile.api.raw; will retry or DLQ",
                    envelopes.size(), ex);
            throw new RuntimeException("ClickHouse insert failed for mobile.api.raw", ex);
        }
    }
}
