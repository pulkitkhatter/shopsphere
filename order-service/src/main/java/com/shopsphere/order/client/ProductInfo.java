package com.shopsphere.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** The few fields of the product-service v2 contract that orders depend on (tolerant reader: unknown fields ignored). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductInfo(String id, String sku, String name, Price price, int stock) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(BigDecimal amount, String currency) {}

    public BigDecimal unitPrice() {
        return price.amount();
    }
}
