package com.shopsphere.notification.service;

import com.shopsphere.notification.event.OrderEvent;
import com.shopsphere.notification.model.Notification;
import com.shopsphere.notification.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Turns an order event into a stored notification (stands in for an e-mail / push sender). Idempotent. */
    public void onOrderEvent(OrderEvent event) {
        String message = switch (event.type()) {
            case ORDER_PLACED -> "Thanks! Your order %s for %s was received.".formatted(event.orderId(), event.total());
            case ORDER_CANCELLED -> "Your order %s was cancelled.".formatted(event.orderId());
        };
        Instant at = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        try {
            repository.insert(new Notification(event.eventId(), event.userId(), event.orderId(), event.type().name(), message, at));
            log.info("Notification stored for user {} ({})", event.userId(), event.type());
        } catch (DuplicateKeyException duplicate) {
            log.info("Event {} already handled, skipping", event.eventId());
        }
    }

    public Page<Notification> forUser(String userId, int page, int size) {
        return repository.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
