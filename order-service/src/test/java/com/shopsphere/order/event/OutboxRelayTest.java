package com.shopsphere.order.event;

import com.shopsphere.order.config.TopicProperties;
import com.shopsphere.order.model.Order;
import com.shopsphere.order.repo.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock OrderRepository orders;
    @Mock KafkaTemplate<String, Object> kafka;
    OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(orders, kafka, new TopicProperties("order-events"));
    }

    private static OrderEvent event(String id, OrderEvent.Type type) {
        return new OrderEvent(id, type, "o-1", "alice", List.of(new OrderEvent.Item("p1", 1)), new BigDecimal("1.00"), Instant.now());
    }

    private static Order orderWith(OrderEvent... events) {
        Order o = new Order();
        o.setId("o-1");
        o.setOutbox(new ArrayList<>(List.of(events)));
        return o;
    }

    @Test
    void publishedEvents_areRemovedFromOutbox_inOrder() {
        OrderEvent placed = event("e1", OrderEvent.Type.ORDER_PLACED);
        OrderEvent cancelled = event("e2", OrderEvent.Type.ORDER_CANCELLED);
        when(orders.findWithPendingEvents(any(Pageable.class))).thenReturn(List.of(orderWith(placed, cancelled)));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        InOrder inOrder = inOrder(kafka, orders);
        inOrder.verify(kafka).send("order-events", "o-1", placed);
        inOrder.verify(orders).removeOutboxEvent("o-1", "e1");
        inOrder.verify(kafka).send("order-events", "o-1", cancelled);
        inOrder.verify(orders).removeOutboxEvent("o-1", "e2");
    }

    @Test
    void whenKafkaFails_eventStaysInOutbox_andLaterEventsAreNotSentOutOfOrder() {
        OrderEvent placed = event("e1", OrderEvent.Type.ORDER_PLACED);
        OrderEvent cancelled = event("e2", OrderEvent.Type.ORDER_CANCELLED);
        when(orders.findWithPendingEvents(any(Pageable.class))).thenReturn(List.of(orderWith(placed, cancelled)));
        when(kafka.send(eq("order-events"), anyString(), eq(placed)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        relay.relay();

        verify(orders, never()).removeOutboxEvent(anyString(), anyString());
        verify(kafka, never()).send(eq("order-events"), anyString(), eq(cancelled));
    }
}
