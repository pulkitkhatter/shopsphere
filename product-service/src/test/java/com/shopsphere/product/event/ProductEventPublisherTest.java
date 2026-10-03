package com.shopsphere.product.event;

import com.shopsphere.product.TestData;
import com.shopsphere.product.config.TopicProperties;
import com.shopsphere.product.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductEventPublisherTest {

    @Mock KafkaTemplate<String, Object> kafka;
    ProductEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ProductEventPublisher(kafka, new TopicProperties("order-events", "product-events"));
    }

    @Test
    void publishesKeyedByProductId_soEventsOfOneProductStayOrdered() {
        Product p = TestData.product("p-1", "SKU-1", "9.99", 3);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(ProductEvent.Type.PRODUCT_CREATED, p);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("product-events"), eq("p-1"), payload.capture());
        ProductEvent event = (ProductEvent) payload.getValue();
        assertThat(event.type()).isEqualTo(ProductEvent.Type.PRODUCT_CREATED);
        assertThat(event.sku()).isEqualTo("SKU-1");
        assertThat(event.eventId()).isNotBlank();
    }

    @Test
    void aKafkaOutage_neverFailsTheCallersRequest() {
        Product p = TestData.product("p-1", "SKU-1", "9.99", 3);
        when(kafka.send(anyString(), anyString(), any())).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> publisher.publish(ProductEvent.Type.PRODUCT_UPDATED, p)).doesNotThrowAnyException();
    }

    @Test
    void anAsyncSendFailure_isLoggedNotThrown() {
        Product p = TestData.product("p-1", "SKU-1", "9.99", 3);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

        assertThatCode(() -> publisher.publish(ProductEvent.Type.PRODUCT_DELETED, p)).doesNotThrowAnyException();
    }
}
