package com.shopsphere.product;

import com.shopsphere.product.model.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TestData {

    private TestData() {}

    public static Product product(String id, String sku, String price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setSku(sku);
        p.setName("Test " + sku);
        p.setDescription("Description of " + sku);
        p.setCategory("electronics");
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setTags(List.of("a", "b"));
        p.setVersion(0L);
        p.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        p.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return p;
    }
}
