package com.shopsphere.order.model;

import com.shopsphere.order.event.OrderEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The "outbox" list holds events that still have to be published to Kafka. Because it lives INSIDE the order
 * document, "save the order" and "remember to publish its event" is one atomic MongoDB write (no distributed
 * transaction, no replica set needed). A background relay publishes them and removes them afterwards.
 */
@Document("orders")
@CompoundIndexes({
        @CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}"),
        // idempotency: one order per (user, Idempotency-Key); only enforced for orders that carry a key
        @CompoundIndex(name = "user_idempotency", def = "{'userId': 1, 'idempotencyKey': 1}",
                unique = true, partialFilter = "{'idempotencyKey': {$exists: true}}"),
        @CompoundIndex(name = "outbox_pending", def = "{'outbox.eventId': 1}", sparse = true)
})
public class Order {

    @Id
    private String id;
    private String userId;
    private String idempotencyKey;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal total;
    private OrderStatus status = OrderStatus.PLACED;
    private Instant createdAt;
    private Instant cancelledAt;
    private List<OrderEvent> outbox = new ArrayList<>();
    @Version
    private Long version;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public List<OrderEvent> getOutbox() { return outbox; }
    public void setOutbox(List<OrderEvent> outbox) { this.outbox = outbox; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
