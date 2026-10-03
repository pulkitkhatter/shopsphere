package com.shopsphere.product.config;

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

    @Bean
    NewTopic orderEventsTopic(TopicProperties t) {
        return TopicBuilder.name(t.orderEvents()).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic orderEventsDlt(TopicProperties t) {
        return TopicBuilder.name(t.orderEvents() + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic productEventsTopic(TopicProperties t) {
        return TopicBuilder.name(t.productEvents()).partitions(3).replicas(1).build();
    }

    /**
     * A failing message is retried 3 times (1s apart) and then parked on "<topic>.DLT" so one poison message
     * never blocks the partition. Deserialisation errors are not retried at all.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
    }
}
