package com.shopsphere.order.service;

import com.shopsphere.order.model.OrderItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure, side-effect free pricing rules: trivial to unit test and reuse. */
public final class OrderPricingCalculator {

    public record Line(String productId, int quantity) {}

    private OrderPricingCalculator() {}

    public static BigDecimal total(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        return items.stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Sums quantities of repeated product lines so stock is checked once per product. */
    public static Map<String, Integer> mergeQuantities(List<Line> lines) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            merged.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return merged;
    }
}
