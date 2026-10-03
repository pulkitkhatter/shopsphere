package com.shopsphere.product.event;

import java.time.Instant;
import java.util.List;

/** Consumer-side copy of the contract published by order-service on "order-events" (no shared class between services). */
public record OrderEvent(String eventId, Type type, String orderId, String userId, List<Item> items, Instant occurredAt) {

    public enum Type { ORDER_PLACED, ORDER_CANCELLED }

    public record Item(String productId, int quantity) {}
}
