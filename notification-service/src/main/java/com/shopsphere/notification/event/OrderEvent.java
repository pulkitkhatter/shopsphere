package com.shopsphere.notification.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** This service's own copy of the order-events contract (consumer-driven: it only declares what it needs). */
public record OrderEvent(String eventId, Type type, String orderId, String userId,
                         List<Item> items, BigDecimal total, Instant occurredAt) {

    public enum Type { ORDER_PLACED, ORDER_CANCELLED }

    public record Item(String productId, int quantity) {}
}
