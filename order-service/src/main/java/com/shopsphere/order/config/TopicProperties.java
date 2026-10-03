package com.shopsphere.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopsphere.topics")
public record TopicProperties(String orderEvents) {
    public TopicProperties {
        if (orderEvents == null) orderEvents = "order-events";
    }
}
