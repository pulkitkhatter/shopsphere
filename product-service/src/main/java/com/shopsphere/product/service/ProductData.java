package com.shopsphere.product.service;

import java.math.BigDecimal;
import java.util.List;

/** Version-independent write model: both the v1 and v2 request DTOs map onto this. */
public record ProductData(String sku, String name, String description, String category,
                          BigDecimal price, int stock, List<String> tags) {
}
