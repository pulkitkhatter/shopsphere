package com.shopsphere.notification.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shopsphere.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventListenerTest {

    /** The exact JSON order-service puts on the wire (extra unknown fields must be tolerated). */
    static final String WIRE_JSON = """
            {"eventId":"evt-1","type":"ORDER_PLACED","orderId":"o-1","userId":"alice",
             "items":[{"productId":"p1","quantity":2}],"total":20.00,"occurredAt":"2026-03-01T12:00:00Z","futureField":"x"}""";

    @Test
    void wireJson_deserialisesIntoTheConsumersOwnContract_andIsHandedToTheService() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OrderEvent event = mapper.readValue(WIRE_JSON, OrderEvent.class);
        NotificationService service = mock(NotificationService.class);

        new OrderEventListener(service).on(event);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(service).onOrderEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(OrderEvent.Type.ORDER_PLACED);
        assertThat(captor.getValue().total()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(captor.getValue().items()).hasSize(1);
    }
}
