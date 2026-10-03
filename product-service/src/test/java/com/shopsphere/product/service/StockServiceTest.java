package com.shopsphere.product.service;

import com.shopsphere.product.event.OrderEvent;
import com.shopsphere.product.event.ProductEvent;
import com.shopsphere.product.event.ProductEventPublisher;
import com.shopsphere.product.model.ProcessedEvent;
import com.shopsphere.product.repo.ProcessedEventRepository;
import com.shopsphere.product.repo.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock ProductRepository products;
    @Mock ProcessedEventRepository processed;
    @Mock ProductEventPublisher events;
    ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(ProductService.CACHE);
    StockService service;

    @BeforeEach
    void setUp() {
        service = new StockService(products, processed, events, cacheManager);
    }

    private static OrderEvent event(OrderEvent.Type type) {
        return new OrderEvent("evt-1", type, "order-1", "alice",
                List.of(new OrderEvent.Item("p1", 2), new OrderEvent.Item("p2", 1)), Instant.now());
    }

    @Test
    void orderPlaced_decrementsStockOfEveryItem() {
        when(products.decrementStockIfAvailable("p1", 2)).thenReturn(true);
        when(products.decrementStockIfAvailable("p2", 1)).thenReturn(true);

        service.apply(event(OrderEvent.Type.ORDER_PLACED));

        verify(products).decrementStockIfAvailable("p1", 2);
        verify(products).decrementStockIfAvailable("p2", 1);
        verifyNoInteractions(events);
    }

    @Test
    void orderPlaced_withInsufficientStock_publishesStockInsufficient() {
        when(products.decrementStockIfAvailable("p1", 2)).thenReturn(false);
        when(products.decrementStockIfAvailable("p2", 1)).thenReturn(true);

        service.apply(event(OrderEvent.Type.ORDER_PLACED));

        verify(events).publish(ProductEvent.Type.STOCK_INSUFFICIENT, "p1", null, null);
    }

    @Test
    void orderCancelled_restoresStock() {
        service.apply(event(OrderEvent.Type.ORDER_CANCELLED));

        verify(products).incrementStock("p1", 2);
        verify(products).incrementStock("p2", 1);
    }

    @Test
    void duplicateDelivery_isIgnored_soStockIsNotDecrementedTwice() {
        doThrow(new DuplicateKeyException("already processed")).when(processed).insert(any(ProcessedEvent.class));

        service.apply(event(OrderEvent.Type.ORDER_PLACED));

        verify(products, never()).decrementStockIfAvailable(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void failureWhileApplying_removesMarker_soRedeliveryIsProcessedAgain() {
        when(products.decrementStockIfAvailable("p1", 2)).thenThrow(new IllegalStateException("mongo down"));

        assertThatThrownBy(() -> service.apply(event(OrderEvent.Type.ORDER_PLACED))).isInstanceOf(IllegalStateException.class);

        verify(processed).deleteById("evt-1");
    }

    @Test
    void stockChange_evictsCachedProduct() {
        cacheManager.getCache(ProductService.CACHE).put("p1", "stale");
        when(products.decrementStockIfAvailable(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

        service.apply(event(OrderEvent.Type.ORDER_PLACED));

        assertThat(cacheManager.getCache(ProductService.CACHE).get("p1")).isNull();
    }
}
