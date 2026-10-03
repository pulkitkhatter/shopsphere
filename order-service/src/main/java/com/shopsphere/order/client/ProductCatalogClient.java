package com.shopsphere.order.client;

import com.shopsphere.common.web.ServiceUnavailableException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Calls product-service (resolved by name through Eureka + client-side load balancing).
 * Wrapped in a circuit breaker: when the catalogue keeps failing, calls fail immediately (503) instead of piling up
 * blocked threads and taking order-service down with it. A 404 is a normal answer, not a failure.
 */
public class ProductCatalogClient implements ProductCatalog {

    private final RestClient client;
    private final CircuitBreaker breaker;

    public ProductCatalogClient(RestClient client, CircuitBreakerFactory<?, ?> breakers) {
        this.client = client;
        this.breaker = breakers.create("product-catalog");
    }

    @Override
    public Optional<ProductInfo> find(String productId) {
        return breaker.run(() -> fetch(productId), this::unavailable);
    }

    private Optional<ProductInfo> fetch(String productId) {
        try {
            return Optional.ofNullable(client.get().uri("/api/v2/products/{id}", productId).retrieve().body(ProductInfo.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    private Optional<ProductInfo> unavailable(Throwable cause) {
        throw new ServiceUnavailableException("Product catalogue is temporarily unavailable, please retry shortly", cause);
    }
}
