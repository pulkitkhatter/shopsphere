package com.shopsphere.product.event;

import com.shopsphere.product.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private final StockService stock;

    public OrderEventListener(StockService stock) {
        this.stock = stock;
    }

    @KafkaListener(topics = "${shopsphere.topics.order-events:order-events}", groupId = "product-service")
    public void onOrderEvent(OrderEvent event) {
        log.info("Received {} for order {}", event.type(), event.orderId());
        stock.apply(event);
    }
}
