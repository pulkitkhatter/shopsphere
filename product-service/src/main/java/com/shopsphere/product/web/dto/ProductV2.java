package com.shopsphere.product.web.dto;

import com.shopsphere.product.model.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** v2 contract: price became an object (breaking change => new major version), plus tags, availability and audit data. */
public record ProductV2(String id, String sku, String name, String description, String category,
                        Price price, int stock, boolean inStock, List<String> tags,
                        long version, Instant createdAt, Instant updatedAt) {

    public record Price(BigDecimal amount, String currency) {}

    public static ProductV2 from(Product p) {
        return new ProductV2(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getCategory(),
                new Price(p.getPrice(), "USD"), p.getStock(), p.getStock() > 0, List.copyOf(p.getTags()),
                p.getVersion() == null ? 0 : p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
