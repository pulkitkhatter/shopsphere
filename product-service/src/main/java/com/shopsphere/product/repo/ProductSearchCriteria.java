package com.shopsphere.product.repo;

import java.math.BigDecimal;

public record ProductSearchCriteria(String q, String category, BigDecimal minPrice, BigDecimal maxPrice, boolean inStockOnly) {
}
