package com.mobobs.api.ingest;

import com.mobobs.api.store.ApiKeyRepository;
import com.mobobs.api.store.BootstrapRunner;
import com.mobobs.api.telemetry.TelemetryDtos.*;
import com.mobobs.api.telemetry.TelemetryEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
    topics = {"mobile.events.raw", "mobile.api.raw", "mobile.errors.raw"},
    partitions = 1,
    brokerProperties = {"listeners=PLAINTEXT://localhost:0"}
)
@ActiveProfiles("test")
class KafkaPublishSmokeTest {

    @Autowired
    private IngestService ingestService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockBean
    private BootstrapRunner bootstrapRunner;

    @MockBean
    private ApiKeyRepository apiKeyRepository;

    @Test
    void validIngest_publishesToCorrectTopic() throws Exception {
        // Set up a consumer on mobile.events.raw
        BlockingQueue<ConsumerRecord<String, TelemetryEnvelope>> records = new LinkedBlockingQueue<>();

        Map<String, Object> consumerProps = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
            ConsumerConfig.GROUP_ID_CONFIG, "test-group",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        JsonDeserializer<TelemetryEnvelope> deserializer = new JsonDeserializer<>(TelemetryEnvelope.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, TelemetryEnvelope> cf =
            new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        ContainerProperties containerProps = new ContainerProperties("mobile.events.raw");
        KafkaMessageListenerContainer<String, TelemetryEnvelope> container =
            new KafkaMessageListenerContainer<>(cf, containerProps);

        container.setupMessageListener((MessageListener<String, TelemetryEnvelope>) records::add);
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());

        // Send a screen_view event
        ScreenViewEvent event = new ScreenViewEvent(
            "evt-1", "screen_view", "sess-1", null,
            "demo-app", "production", "1.0.0", "v1.0.0",
            "ios", "iPhone 15", "17.0",
            OffsetDateTime.now(), null, "HomeScreen"
        );
        TelemetryBatch batch = new TelemetryBatch(List.of(event));

        ingestService.processBatch(batch, 1L, "127.0.0.1");

        ConsumerRecord<String, TelemetryEnvelope> received = records.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo("sess-1");
        assertThat(received.value().event()).isInstanceOf(ScreenViewEvent.class);

        container.stop();
    }
}
