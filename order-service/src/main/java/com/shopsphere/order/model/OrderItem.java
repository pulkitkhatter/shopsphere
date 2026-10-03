package com.shopsphere.order.model;

import java.math.BigDecimal;

/** Snapshot of the product at order time: later catalogue price changes must never alter an existing order. */
public record OrderItem(String productId, String sku, String name, BigDecimal unitPrice, int quantity) {
}
