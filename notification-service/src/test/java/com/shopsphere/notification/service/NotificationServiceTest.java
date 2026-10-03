package com.shopsphere.notification.service;

import com.shopsphere.notification.event.OrderEvent;
import com.shopsphere.notification.model.Notification;
import com.shopsphere.notification.repo.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repository;
    @InjectMocks NotificationService service;

    private static OrderEvent event(OrderEvent.Type type) {
        return new OrderEvent("evt-1", type, "o-1", "alice", List.of(new OrderEvent.Item("p1", 2)),
                new BigDecimal("20.00"), Instant.parse("2026-03-01T12:00:00Z"));
    }

    @Test
    void orderPlaced_storesNotificationForTheOrdersOwner() {
        service.onOrderEvent(event(OrderEvent.Type.ORDER_PLACED));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).insert(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getUserId()).isEqualTo("alice");
        assertThat(n.getEventId()).isEqualTo("evt-1");
        assertThat(n.getType()).isEqualTo("ORDER_PLACED");
        assertThat(n.getMessage()).contains("o-1").contains("20.00");
        assertThat(n.getCreatedAt()).isEqualTo(Instant.parse("2026-03-01T12:00:00Z"));
    }

    @Test
    void orderCancelled_storesCancellationMessage() {
        service.onOrderEvent(event(OrderEvent.Type.ORDER_CANCELLED));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("cancelled");
    }

    @Test
    void redeliveredEvent_isIgnoredSilently() {
        doThrow(new DuplicateKeyException("eventId")).when(repository).insert(any(Notification.class));

        assertThatCode(() -> service.onOrderEvent(event(OrderEvent.Type.ORDER_PLACED))).doesNotThrowAnyException();
    }

    @Test
    void forUser_queriesOnlyThatUser_newestFirst() {
        when(repository.findByUserId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.forUser("alice", 1, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserId(org.mockito.ArgumentMatchers.eq("alice"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }
}
