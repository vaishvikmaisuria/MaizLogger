package com.mobobs.worker.consumers;

import com.mobobs.worker.clickhouse.ClickHouseSink;
import com.mobobs.worker.telemetry.TelemetryDtos.AppStartEvent;
import com.mobobs.worker.telemetry.TelemetryEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link EventsConsumer} — no Spring context, no Kafka broker.
 *
 * Verifies the at-least-once contract directly:
 * - ack.acknowledge() is called AFTER a successful sink write.
 * - ack.acknowledge() is NOT called when the sink throws.
 */
class EventsConsumerUnitTest {

    private final ClickHouseSink sink = mock(ClickHouseSink.class);
    private final Acknowledgment ack = mock(Acknowledgment.class);
    private final EventsConsumer consumer = new EventsConsumer(sink);

    @Test
    void shouldAcknowledgeOffsetAfterSuccessfulSinkWrite() throws Exception {
        List<TelemetryEnvelope> batch = List.of(buildEnvelope());

        consumer.consume(batch, ack);

        verify(sink).insertEvents(batch);
        verify(ack).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeWhenSinkThrows() throws Exception {
        doThrow(new RuntimeException("clickhouse down")).when(sink).insertEvents(anyList());

        List<TelemetryEnvelope> batch = List.of(buildEnvelope());

        assertThrows(RuntimeException.class, () -> consumer.consume(batch, ack));
        verify(ack, never()).acknowledge();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TelemetryEnvelope buildEnvelope() {
        String sessionId = UUID.randomUUID().toString();
        AppStartEvent event = new AppStartEvent(
                UUID.randomUUID().toString(), "app_start", sessionId, null,
                "TestApp", "test", "1.0.0", "1.0.0", "ios",
                "iPhone14", "17.0", null, OffsetDateTime.now(), true, 250.0);
        return new TelemetryEnvelope(1, OffsetDateTime.now(), "127.0.0.1", 1L, event);
    }
}
