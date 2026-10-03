package com.shopsphere.order.service;

import com.shopsphere.common.web.ServiceUnavailableException;
import com.shopsphere.order.client.ProductCatalogClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductCatalogClientTest {

    MockRestServiceServer server;
    ProductCatalogClient client;

    /** Behaves like a closed breaker: runs the call, hands any exception to the fallback. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static CircuitBreakerFactory<?, ?> passThroughBreaker() {
        CircuitBreaker breaker = new CircuitBreaker() {
            @Override
            public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
                try {
                    return toRun.get();
                } catch (Throwable t) {
                    return fallback.apply(t);
                }
            }
        };
        CircuitBreakerFactory factory = org.mockito.Mockito.mock(CircuitBreakerFactory.class);
        org.mockito.Mockito.when(factory.create(org.mockito.ArgumentMatchers.anyString())).thenReturn(breaker);
        return factory;
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://product-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProductCatalogClient(builder.build(), passThroughBreaker());
    }

    @Test
    void find_parsesV2Contract_ignoringUnknownFields() {
        server.expect(requestTo("http://product-service/api/v2/products/abc"))
                .andRespond(withSuccess("""
                        {"id":"abc","sku":"S-1","name":"Thing","price":{"amount":12.50,"currency":"USD"},"stock":7,
                         "inStock":true,"tags":["x"],"version":3,"somethingNew":"added later"}""", MediaType.APPLICATION_JSON));

        var product = client.find("abc").orElseThrow();

        assertThat(product.sku()).isEqualTo("S-1");
        assertThat(product.unitPrice()).isEqualByComparingTo("12.50");
        assertThat(product.stock()).isEqualTo(7);
    }

    @Test
    void find_404_meansProductDoesNotExist_notAnOutage() {
        server.expect(requestTo("http://product-service/api/v2/products/ghost")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.find("ghost")).isEmpty();
    }

    @Test
    void find_500_isReportedAsServiceUnavailable() {
        server.expect(requestTo("http://product-service/api/v2/products/abc")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.find("abc")).isInstanceOf(ServiceUnavailableException.class);
    }
}
