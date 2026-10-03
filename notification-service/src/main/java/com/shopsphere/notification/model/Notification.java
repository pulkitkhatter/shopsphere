package com.shopsphere.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("notifications")
@CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
public class Notification {

    @Id
    private String id;
    @Indexed(unique = true)          // idempotent consumer: the same Kafka event can never create two notifications
    private String eventId;
    private String userId;
    private String orderId;
    private String type;
    private String message;
    private Instant createdAt;

    public Notification() {}

    public Notification(String eventId, String userId, String orderId, String type, String message, Instant createdAt) {
        this.eventId = eventId;
        this.userId = userId;
        this.orderId = orderId;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getUserId() { return userId; }
    public String getOrderId() { return orderId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
