package com.shopsphere.product.event;

import com.shopsphere.product.config.TopicProperties;
import com.shopsphere.product.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Component
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final TopicProperties topics;

    public ProductEventPublisher(KafkaTemplate<String, Object> kafka, TopicProperties topics) {
        this.kafka = kafka;
        this.topics = topics;
    }

    public void publish(ProductEvent.Type type, Product product) {
        publish(type, product.getId(), product.getSku(), product.getPrice());
    }

    public void publish(ProductEvent.Type type, String productId, String sku, BigDecimal price) {
        ProductEvent event = new ProductEvent(UUID.randomUUID().toString(), type, productId, sku, price, Instant.now());
        // keyed by product id => all events of one product stay ordered inside one partition.
        // Failure to publish must not fail the HTTP request (the catalogue write already succeeded): log and move on.
        try {
            kafka.send(topics.productEvents(), productId, event)
                    .whenComplete((r, ex) -> {
                        if (ex != null) log.warn("Could not publish {} for product {}: {}", type, productId, ex.toString());
                    });
        } catch (RuntimeException e) {
            log.warn("Could not publish {} for product {}: {}", type, productId, e.toString());
        }
    }
}
