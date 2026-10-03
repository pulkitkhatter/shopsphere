package com.shopsphere.product.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shopsphere.product.service.StockService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderEventListenerTest {

    /** Exactly what order-service puts on the wire; unknown fields (total, futureField) must be tolerated. */
    static final String WIRE_JSON = """
            {"eventId":"evt-9","type":"ORDER_CANCELLED","orderId":"o-1","userId":"alice",
             "items":[{"productId":"p1","quantity":2},{"productId":"p2","quantity":1}],
             "total":20.00,"occurredAt":"2026-03-01T12:00:00Z","futureField":"x"}""";

    @Test
    void wireJson_matchesTheConsumersContract_andIsHandedToStockService() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OrderEvent event = mapper.readValue(WIRE_JSON, OrderEvent.class);
        StockService stock = mock(StockService.class);

        new OrderEventListener(stock).onOrderEvent(event);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(stock).apply(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(OrderEvent.Type.ORDER_CANCELLED);
        assertThat(captor.getValue().items()).extracting(OrderEvent.Item::quantity).containsExactly(2, 1);
    }
}
