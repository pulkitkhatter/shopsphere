package com.shopsphere.order.web.dto;

import com.shopsphere.order.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(String id, String status, BigDecimal total, List<Item> items, Instant createdAt, Instant cancelledAt) {

    public record Item(String productId, String sku, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}

    public static OrderResponse from(Order o) {
        List<Item> items = o.getItems().stream()
                .map(i -> new Item(i.productId(), i.sku(), i.name(), i.unitPrice(), i.quantity(),
                        i.unitPrice().multiply(BigDecimal.valueOf(i.quantity()))))
                .toList();
        return new OrderResponse(o.getId(), o.getStatus().name(), o.getTotal(), items, o.getCreatedAt(), o.getCancelledAt());
    }
}
