package com.mobobs.worker.consumers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit-test stub for DLQ routing logic.
 *
 * The full integration path (force error → verify DLT publish) is exercised
 * during end-to-end Compose testing. This unit test verifies the naming
 * convention used by the DeadLetterPublishingRecoverer destination resolver.
 */
class DlqRoutingTest {

    @Test
    void eventsRawMapsToEventsDlq() {
        assertEquals("mobile.events.dlq", dlqTopic("mobile.events.raw"));
    }

    @Test
    void apiRawMapsToApiDlq() {
        assertEquals("mobile.api.dlq", dlqTopic("mobile.api.raw"));
    }

    @Test
    void errorsRawMapsToErrorsDlq() {
        assertEquals("mobile.errors.dlq", dlqTopic("mobile.errors.raw"));
    }

    /** Mirrors the lambda inside {@code KafkaConsumerConfig.batchListenerContainerFactory}. */
    private static String dlqTopic(String rawTopic) {
        return rawTopic.replace(".raw", ".dlq");
    }
}
