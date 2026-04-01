package com.mobobs.worker.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.BiFunction;

/**
 * DLQ destination resolver extracted as a standalone bean so it can be
 * documented, reused, and unit-tested independently of the container factory.
 *
 * Naming convention:
 *   mobile.events.raw → mobile.events.dlq
 *   mobile.api.raw    → mobile.api.dlq
 *   mobile.errors.raw → mobile.errors.dlq
 *
 * Partition -1 tells {@code DeadLetterPublishingRecoverer} to let Kafka
 * choose the target partition (round-robin / default partitioner).
 */
@Configuration
public class DlqConfig {

    @Bean
    public BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> dlqDestinationResolver() {
        return (record, ex) -> new TopicPartition(record.topic().replace(".raw", ".dlq"), -1);
    }
}
