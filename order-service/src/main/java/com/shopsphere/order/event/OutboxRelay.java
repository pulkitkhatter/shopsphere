package com.shopsphere.order.event;

import com.shopsphere.order.config.TopicProperties;
import com.shopsphere.order.model.Order;
import com.shopsphere.order.repo.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Transactional-outbox relay. Delivery is at-least-once: if the process dies after Kafka accepted a message but before
 * the event is removed from the outbox, it is sent again - consumers de-duplicate on eventId (see product-service
 * StockService). With several order-service instances an event may occasionally be sent twice for the same reason
 * (a lock such as ShedLock would avoid even that).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int BATCH = 50;

    private final OrderRepository orders;
    private final KafkaTemplate<String, Object> kafka;
    private final TopicProperties topics;

    public OutboxRelay(OrderRepository orders, KafkaTemplate<String, Object> kafka, TopicProperties topics) {
        this.orders = orders;
        this.kafka = kafka;
        this.topics = topics;
    }

    @Scheduled(fixedDelayString = "${shopsphere.outbox.poll-interval-ms:1000}")
    public void relay() {
        List<Order> pending = orders.findWithPendingEvents(PageRequest.of(0, BATCH));
        for (Order order : pending) {
            for (OrderEvent event : List.copyOf(order.getOutbox())) {
                try {
                    // key = order id: all events of one order go to the same partition => consumers see them in order
                    kafka.send(topics.orderEvents(), order.getId(), event).get(5, TimeUnit.SECONDS);
                    orders.removeOutboxEvent(order.getId(), event.eventId());
                    log.info("Published {} for order {}", event.type(), order.getId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.warn("Kafka unavailable, will retry {} for order {}: {}", event.type(), order.getId(), e.toString());
                    return;      // keep ordering: do not publish later events before this one
                }
            }
        }
    }
}
