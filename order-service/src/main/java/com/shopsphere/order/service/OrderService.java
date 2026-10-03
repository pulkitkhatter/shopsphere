package com.shopsphere.order.service;

import com.shopsphere.common.web.ConflictException;
import com.shopsphere.common.web.NotFoundException;
import com.shopsphere.order.client.ProductCatalog;
import com.shopsphere.order.client.ProductInfo;
import com.shopsphere.order.event.OrderEvent;
import com.shopsphere.order.model.Order;
import com.shopsphere.order.model.OrderItem;
import com.shopsphere.order.model.OrderStatus;
import com.shopsphere.order.repo.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public record PlacedOrder(Order order, boolean created) {}

    private final OrderRepository orders;
    private final ProductCatalog catalog;
    private final Clock clock;

    public OrderService(OrderRepository orders, ProductCatalog catalog, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * Idempotent when an Idempotency-Key is supplied: a client that times out and retries gets the original order
     * back instead of a duplicate (and a second charge/shipment).
     */
    public PlacedOrder place(String userId, List<OrderPricingCalculator.Line> lines, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return new PlacedOrder(existing.get(), false);
            }
        }

        List<OrderItem> items = new ArrayList<>();
        OrderPricingCalculator.mergeQuantities(lines).forEach((productId, quantity) -> {
            ProductInfo product = catalog.find(productId)
                    .orElseThrow(() -> new InvalidOrderException("Unknown product " + productId));
            if (product.stock() < quantity) {
                throw new InvalidOrderException("Not enough stock for '" + product.name() + "': requested " + quantity
                        + ", available " + product.stock());
            }
            // price is taken from the catalogue, never from the client: the client cannot choose what it pays
            items.add(new OrderItem(product.id(), product.sku(), product.name(), product.unitPrice(), quantity));
        });

        Order order = new Order();
        order.setUserId(userId);
        order.setIdempotencyKey(idempotencyKey);
        order.setItems(items);
        order.setTotal(OrderPricingCalculator.total(items));
        order.setCreatedAt(clock.instant());
        order.getOutbox().add(event(order, OrderEvent.Type.ORDER_PLACED, order.getCreatedAt()));
        try {
            Order saved = orders.save(order);
            return new PlacedOrder(saved, true);
        } catch (DuplicateKeyException race) {        // two concurrent retries with the same key: the unique index picks the winner
            return new PlacedOrder(orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElseThrow(), false);
        }
    }

    public Order get(String orderId, String userId, boolean admin) {
        Order order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        if (!admin && !order.getUserId().equals(userId)) {
            // 404, not 403: do not reveal that someone else's order id exists (prevents id enumeration / IDOR probing)
            throw new NotFoundException("Order " + orderId + " not found");
        }
        return order;
    }

    public Page<Order> mine(String userId, Pageable pageable) {
        return orders.findByUserId(userId, pageable);
    }

    public Page<Order> all(Pageable pageable) {
        return orders.findAll(pageable);
    }

    public Order cancel(String orderId, String userId, boolean admin) {
        Order order = get(orderId, userId, admin);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("Order is already cancelled");
        }
        Instant now = clock.instant();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.getOutbox().add(event(order, OrderEvent.Type.ORDER_CANCELLED, now));
        log.info("Order {} cancelled by {}", orderId, userId);
        return orders.save(order);      // @Version: two simultaneous cancels cannot both succeed (second gets 409)
    }

    private static OrderEvent event(Order order, OrderEvent.Type type, Instant at) {
        // the order id is assigned by Mongo on save; generate it up front so the event can reference it
        if (order.getId() == null) {
            order.setId(new org.bson.types.ObjectId().toHexString());
        }
        List<OrderEvent.Item> items = order.getItems().stream()
                .map(i -> new OrderEvent.Item(i.productId(), i.quantity())).toList();
        return new OrderEvent(UUID.randomUUID().toString(), type, order.getId(), order.getUserId(), items, order.getTotal(), at);
    }
}
