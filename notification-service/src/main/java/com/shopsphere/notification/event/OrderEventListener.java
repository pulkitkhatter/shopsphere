package com.shopsphere.notification.event;

import com.shopsphere.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final NotificationService service;

    public OrderEventListener(NotificationService service) {
        this.service = service;
    }

    /** Own consumer group => receives every order event independently of product-service (pub/sub fan-out). */
    @KafkaListener(topics = "${shopsphere.topics.order-events:order-events}", groupId = "notification-service")
    public void on(OrderEvent event) {
        service.onOrderEvent(event);
    }
}
