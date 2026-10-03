package com.shopsphere.order.service;

import com.shopsphere.common.web.ConflictException;
import com.shopsphere.common.web.NotFoundException;
import com.shopsphere.common.web.ServiceUnavailableException;
import com.shopsphere.order.client.ProductCatalog;
import com.shopsphere.order.client.ProductInfo;
import com.shopsphere.order.event.OrderEvent;
import com.shopsphere.order.model.Order;
import com.shopsphere.order.model.OrderStatus;
import com.shopsphere.order.repo.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orders;
    @Mock ProductCatalog catalog;
    final Clock clock = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);
    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, catalog, clock);
    }

    private static ProductInfo product(String id, String price, int stock) {
        return new ProductInfo(id, "SKU-" + id, "Product " + id, new ProductInfo.Price(new BigDecimal(price), "USD"), stock);
    }

    private void saveReturnsArgument() {
        when(orders.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void place_pricesFromCatalogue_computesTotal_andQueuesOrderPlacedEvent() {
        when(catalog.find("p1")).thenReturn(Optional.of(product("p1", "10.00", 5)));
        when(catalog.find("p2")).thenReturn(Optional.of(product("p2", "2.50", 10)));
        saveReturnsArgument();

        var placed = service.place("alice", List.of(new OrderPricingCalculator.Line("p1", 2), new OrderPricingCalculator.Line("p2", 4)), null);

        Order order = placed.order();
        assertThat(placed.created()).isTrue();
        assertThat(order.getUserId()).isEqualTo("alice");
        assertThat(order.getTotal()).isEqualByComparingTo("30.00");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(order.getOutbox()).hasSize(1);
        OrderEvent event = order.getOutbox().get(0);
        assertThat(event.type()).isEqualTo(OrderEvent.Type.ORDER_PLACED);
        assertThat(event.orderId()).isEqualTo(order.getId()).isNotBlank();
        assertThat(event.items()).containsExactly(new OrderEvent.Item("p1", 2), new OrderEvent.Item("p2", 4));
        assertThat(event.total()).isEqualByComparingTo("30.00");
    }

    @Test
    void place_mergesDuplicateLines_soStockIsCheckedAgainstTheSum() {
        when(catalog.find("p1")).thenReturn(Optional.of(product("p1", "10.00", 5)));

        assertThatThrownBy(() -> service.place("alice",
                List.of(new OrderPricingCalculator.Line("p1", 3), new OrderPricingCalculator.Line("p1", 3)), null))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("requested 6")
                .hasMessageContaining("available 5");
        verify(orders, never()).save(any());
    }

    @Test
    void place_unknownProduct_isRejected() {
        when(catalog.find("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.place("alice", List.of(new OrderPricingCalculator.Line("ghost", 1)), null))
                .isInstanceOf(InvalidOrderException.class).hasMessageContaining("ghost");
    }

    @Test
    void place_whenCatalogueIsDown_propagatesServiceUnavailable_andSavesNothing() {
        when(catalog.find("p1")).thenThrow(new ServiceUnavailableException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.place("alice", List.of(new OrderPricingCalculator.Line("p1", 1)), null))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void place_withKnownIdempotencyKey_returnsOriginalOrder_withoutTouchingCatalogueOrDb() {
        Order original = new Order();
        original.setId("o-1");
        when(orders.findByUserIdAndIdempotencyKey("alice", "key-12345")).thenReturn(Optional.of(original));

        var placed = service.place("alice", List.of(new OrderPricingCalculator.Line("p1", 1)), "key-12345");

        assertThat(placed.created()).isFalse();
        assertThat(placed.order()).isSameAs(original);
        verifyNoInteractions(catalog);
        verify(orders, never()).save(any());
    }

    @Test
    void place_concurrentRetriesWithSameKey_loserGetsTheWinnersOrder() {
        Order winner = new Order();
        winner.setId("o-win");
        when(orders.findByUserIdAndIdempotencyKey("alice", "key-12345")).thenReturn(Optional.empty(), Optional.of(winner));
        when(catalog.find("p1")).thenReturn(Optional.of(product("p1", "1.00", 5)));
        when(orders.save(any())).thenThrow(new DuplicateKeyException("idempotency index"));

        var placed = service.place("alice", List.of(new OrderPricingCalculator.Line("p1", 1)), "key-12345");

        assertThat(placed.created()).isFalse();
        assertThat(placed.order()).isSameAs(winner);
    }

    @Test
    void get_ownOrder_isReturned() {
        Order order = orderOf("o-1", "alice");
        when(orders.findById("o-1")).thenReturn(Optional.of(order));

        assertThat(service.get("o-1", "alice", false)).isSameAs(order);
    }

    @Test
    void get_someoneElsesOrder_looksLikeNotFound() {
        when(orders.findById("o-1")).thenReturn(Optional.of(orderOf("o-1", "alice")));

        assertThatThrownBy(() -> service.get("o-1", "mallory", false)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_adminCanReadAnyOrder() {
        Order order = orderOf("o-1", "alice");
        when(orders.findById("o-1")).thenReturn(Optional.of(order));

        assertThat(service.get("o-1", "admin", true)).isSameAs(order);
    }

    @Test
    void cancel_marksCancelled_andQueuesCancelledEvent() {
        Order order = orderOf("o-1", "alice");
        when(orders.findById("o-1")).thenReturn(Optional.of(order));
        saveReturnsArgument();

        Order cancelled = service.cancel("o-1", "alice", false);

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isEqualTo(clock.instant());
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getOutbox()).extracting(OrderEvent::type).containsExactly(OrderEvent.Type.ORDER_CANCELLED);
    }

    @Test
    void cancel_alreadyCancelled_isConflict() {
        Order order = orderOf("o-1", "alice");
        order.setStatus(OrderStatus.CANCELLED);
        when(orders.findById("o-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel("o-1", "alice", false)).isInstanceOf(ConflictException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void cancel_someoneElsesOrder_isNotFound_andNotModified() {
        when(orders.findById("o-1")).thenReturn(Optional.of(orderOf("o-1", "alice")));

        assertThatThrownBy(() -> service.cancel("o-1", "mallory", false)).isInstanceOf(NotFoundException.class);
        verify(orders, never()).save(any());
    }

    private static Order orderOf(String id, String user) {
        Order o = new Order();
        o.setId(id);
        o.setUserId(user);
        o.setTotal(new BigDecimal("5.00"));
        o.setItems(List.of(new com.shopsphere.order.model.OrderItem("p1", "SKU-p1", "P1", new BigDecimal("5.00"), 1)));
        return o;
    }
}
