package com.mobobs.worker.kafka;

import com.mobobs.worker.telemetry.TelemetryEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Batch Kafka consumer configuration with DLQ support.
 *
 * DLQ naming convention: replace ".raw" with ".dlq" in the source topic name.
 *   mobile.events.raw → mobile.events.dlq
 *   mobile.api.raw    → mobile.api.dlq
 *   mobile.errors.raw → mobile.errors.dlq
 *
 * Retry policy: 5 attempts with 500 ms fixed back-off, then publish to DLQ.
 * Non-retryable: DeserializationException, IllegalArgumentException (mapping errors).
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    /**
     * Lightweight producer template used exclusively by the DeadLetterPublishingRecoverer.
     * Uses JsonSerializer so DLQ records remain human-readable.
     */
    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    /**
     * Batch listener container factory shared by all topic consumers.
     * - Batch mode: true
     * - AckMode: MANUAL_IMMEDIATE — offsets committed only after successful CH write
     * - ErrorHandler: DefaultErrorHandler with DLQ recoverer
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TelemetryEnvelope> batchListenerContainerFactory(
            ConsumerFactory<String, TelemetryEnvelope> consumerFactory,
            KafkaTemplate<String, Object> dlqKafkaTemplate,
            BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> dlqDestinationResolver) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqKafkaTemplate, dlqDestinationResolver);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(500L, 5L)); // 5 retries × 500 ms

        // These exceptions are never transient; skip retries and go straight to DLQ
        errorHandler.addNotRetryableExceptions(
                DeserializationException.class,
                IllegalArgumentException.class);

        ConcurrentKafkaListenerContainerFactory<String, TelemetryEnvelope> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
