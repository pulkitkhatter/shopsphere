package com.shopsphere.product.event;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductEvent(String eventId, Type type, String productId, String sku, BigDecimal price, Instant occurredAt) {

    public enum Type { PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED, STOCK_INSUFFICIENT }
}
