package com.shopsphere.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /**
     * Every service that uses a topic declares it identically (create-if-missing is idempotent). Without this the
     * consumer could subscribe first, the broker would auto-create the topic with 1 partition, and this service would
     * keep seeing only partition 0 until its metadata refreshed.
     */
    @Bean
    NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic orderEventsDlt() {
        return TopicBuilder.name("order-events.DLT").partitions(3).replicas(1).build();
    }

    /** Retry 3x, then park the record on "<topic>.DLT" so one bad message never blocks the partition. */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
    }
}
