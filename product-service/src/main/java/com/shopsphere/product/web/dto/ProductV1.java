package com.shopsphere.product.web.dto;

import com.shopsphere.product.model.Product;

import java.math.BigDecimal;

/** v1 contract: flat price. Frozen - never change it, add to v2 instead. */
public record ProductV1(String id, String sku, String name, String description, String category, BigDecimal price, int stock) {

    public static ProductV1 from(Product p) {
        return new ProductV1(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getCategory(), p.getPrice(), p.getStock());
    }
}
