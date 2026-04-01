package com.mobobs.worker.consumers;

import com.mobobs.worker.clickhouse.ClickHouseSink;
import com.mobobs.worker.telemetry.TelemetryDtos.AppStartEvent;
import com.mobobs.worker.telemetry.TelemetryEnvelope;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Integration test for {@link EventsConsumer}.
 *
 * Uses Embedded Kafka so no external broker is required.
 * {@link ClickHouseSink} is mocked — no ClickHouse instance needed.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"mobile.events.raw", "mobile.events.dlq", "mobile.api.raw", "mobile.errors.raw"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ActiveProfiles("test")
@DirtiesContext
class EventsConsumerTest {

    @MockBean
    private ClickHouseSink clickHouseSink;

    @Autowired
    private org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaTemplate<String, TelemetryEnvelope> testTemplate;

    @BeforeEach
    void setUpProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,  embeddedKafkaBroker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        testTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Test
    void shouldCallSinkWhenValidEnvelopeReceived() throws Exception {
        TelemetryEnvelope envelope = buildAppStartEnvelope();

        testTemplate.send("mobile.events.raw", envelope.event().sessionId(), envelope).get();

        // Verify ClickHouseSink.insertEvents() is called within 10 s
        verify(clickHouseSink, timeout(10_000).atLeastOnce()).insertEvents(anyList());
    }

    @Test
    void shouldPublishToDlqWhenSinkThrowsNonRetryableException() throws Exception {
        // IllegalArgumentException is registered as non-retryable → goes straight to DLQ
        doThrow(new IllegalArgumentException("unmappable event type"))
                .when(clickHouseSink).insertEvents(anyList());

        // Subscribe a consumer to the DLQ topic before publishing
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlq-verify-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   ByteArrayDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        Consumer<String, byte[]> dlqConsumer =
                new DefaultKafkaConsumerFactory<String, byte[]>(consumerProps).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dlqConsumer, "mobile.events.dlq");

        // Publish a message that will fail and be routed to the DLQ
        testTemplate.send("mobile.events.raw", buildAppStartEnvelope().event().sessionId(),
                buildAppStartEnvelope()).get();

        // Verify at least one record lands on the DLQ
        ConsumerRecords<String, byte[]> dlqRecords =
                KafkaTestUtils.getRecords(dlqConsumer, Duration.ofSeconds(15));
        assertThat(dlqRecords.count()).isGreaterThan(0);

        dlqConsumer.close();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TelemetryEnvelope buildAppStartEnvelope() {
        String sessionId = UUID.randomUUID().toString();
        AppStartEvent event = new AppStartEvent(
                UUID.randomUUID().toString(),   // eventId
                "app_start",                    // eventType
                sessionId,                      // sessionId
                null,                           // userId
                "TestApp",                      // appName
                "test",                         // environment
                "1.0.0",                        // appVersion
                "1.0.0",                        // release
                "ios",                          // platform
                "iPhone14",                     // deviceModel
                "17.0",                         // osVersion
                null,                           // traceId
                OffsetDateTime.now(),           // timestamp
                true,                           // coldStart
                250.0                           // durationMs
        );
        return new TelemetryEnvelope(1, OffsetDateTime.now(), "127.0.0.1", 1L, event);
    }
}
