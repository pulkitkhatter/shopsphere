package com.shopsphere.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Contract published on the "order-events" topic. Consumers keep their own copy of this shape. */
public record OrderEvent(String eventId, Type type, String orderId, String userId,
                         List<Item> items, BigDecimal total, Instant occurredAt) {

    public enum Type { ORDER_PLACED, ORDER_CANCELLED }

    public record Item(String productId, int quantity) {}
}
