package com.shopsphere.order.event;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test against a real (embedded, in-JVM) Kafka broker: the JSON that actually travels over the wire is what
 * product-service and notification-service are written against - field names, enum names, and no Java type headers.
 */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = "order-events-test")
class OrderEventWireFormatTest {

    @Autowired EmbeddedKafkaBroker broker;

    @Test
    void orderEvent_isPublishedAsPlainJson_withStableFieldNames() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(broker);
        producerProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        var producerFactory = new DefaultKafkaProducerFactory<String, Object>(producerProps, new StringSerializer(), new JsonSerializer<>());
        var template = new KafkaTemplate<>(producerFactory);

        OrderEvent event = new OrderEvent("evt-1", OrderEvent.Type.ORDER_PLACED, "o-1", "alice",
                List.of(new OrderEvent.Item("p1", 2)), new BigDecimal("20.00"), Instant.parse("2026-03-01T12:00:00Z"));
        template.send("order-events-test", "o-1", event);
        template.flush();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("wire-test", "true", broker);
        consumerProps.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "order-events-test");
            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "order-events-test");

            assertThat(record.key()).isEqualTo("o-1");
            assertThat(record.headers().lastHeader("__TypeId__")).isNull();       // no Java class names leak into the contract
            assertThat(record.value())
                    .contains("\"eventId\":\"evt-1\"")
                    .contains("\"type\":\"ORDER_PLACED\"")
                    .contains("\"orderId\":\"o-1\"")
                    .contains("\"userId\":\"alice\"")
                    .contains("\"productId\":\"p1\"")
                    .contains("\"quantity\":2");
        }
    }
}
