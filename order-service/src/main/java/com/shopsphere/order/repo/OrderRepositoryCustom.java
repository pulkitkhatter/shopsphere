package com.shopsphere.order.repo;

public interface OrderRepositoryCustom {
    /** Atomically removes one delivered event from an order's outbox ($pull). */
    void removeOutboxEvent(String orderId, String eventId);
}
