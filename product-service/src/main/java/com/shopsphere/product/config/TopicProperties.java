package com.shopsphere.product.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopsphere.topics")
public record TopicProperties(String orderEvents, String productEvents) {
    public TopicProperties {
        if (orderEvents == null) orderEvents = "order-events";
        if (productEvents == null) productEvents = "product-events";
    }
}
