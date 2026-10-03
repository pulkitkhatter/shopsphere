package com.shopsphere.product.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Marker for the idempotent consumer: Kafka delivers at-least-once, so we remember which events were applied. */
@Document("processed_events")
public class ProcessedEvent {

    @Id
    private String eventId;
    @Indexed(expireAfter = "7d")   // markers are only needed while redelivery is possible
    private Instant processedAt = Instant.now();

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}
