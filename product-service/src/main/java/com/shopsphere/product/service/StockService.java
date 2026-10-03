package com.shopsphere.product.service;

import com.shopsphere.product.event.OrderEvent;
import com.shopsphere.product.event.ProductEvent;
import com.shopsphere.product.event.ProductEventPublisher;
import com.shopsphere.product.model.ProcessedEvent;
import com.shopsphere.product.repo.ProcessedEventRepository;
import com.shopsphere.product.repo.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final ProductRepository products;
    private final ProcessedEventRepository processed;
    private final ProductEventPublisher events;
    private final CacheManager cacheManager;

    public StockService(ProductRepository products, ProcessedEventRepository processed,
                        ProductEventPublisher events, CacheManager cacheManager) {
        this.products = products;
        this.processed = processed;
        this.events = events;
        this.cacheManager = cacheManager;
    }

    /**
     * Applies an order event to stock. Idempotent: Kafka may deliver the same event twice, which must not
     * decrement stock twice. The marker is written first (unique _id) and removed again if applying fails,
     * so a retry after a crash is still processed.
     */
    public void apply(OrderEvent event) {
        try {
            processed.insert(new ProcessedEvent(event.eventId()));
        } catch (DuplicateKeyException duplicate) {
            log.info("Skipping already processed event {}", event.eventId());
            return;
        }
        try {
            switch (event.type()) {
                case ORDER_PLACED -> event.items().forEach(i -> reserve(event, i));
                case ORDER_CANCELLED -> event.items().forEach(i -> release(i.productId(), i.quantity()));
            }
        } catch (RuntimeException e) {
            processed.deleteById(event.eventId());
            throw e;
        }
    }

    private void reserve(OrderEvent event, OrderEvent.Item item) {
        boolean ok = products.decrementStockIfAvailable(item.productId(), item.quantity());
        evict(item.productId());
        if (!ok) {
            log.warn("Order {}: insufficient stock for product {} (wanted {})", event.orderId(), item.productId(), item.quantity());
            events.publish(ProductEvent.Type.STOCK_INSUFFICIENT, item.productId(), null, null);
        }
    }

    private void release(String productId, int quantity) {
        products.incrementStock(productId, quantity);
        evict(productId);
    }

    private void evict(String productId) {
        Cache cache = cacheManager.getCache(ProductService.CACHE);
        if (cache != null) {
            cache.evict(productId);
        }
    }
}
