package com.shopsphere.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopsphere.gateway.web.GatewayErrorHandler;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler = new GatewayErrorHandler(new ObjectMapper());

    private MockServerWebExchange handle(Throwable ex) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v2/products"));
        handler.handle(exchange, ex).block();
        return exchange;
    }

    @Test
    void noInstanceRegistered_is503ProblemJson() {
        var exchange = handle(NotFoundException.create(false, "Unable to find instance for product-service"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"title\":\"Service unavailable\"")
                .doesNotContain("product-service");           // internal service names are not leaked to clients
    }

    @Test
    void connectionRefused_is503_evenWhenWrapped() {
        var exchange = handle(new RuntimeException("wrapper", new ConnectException("Connection refused: /10.0.0.5:8082")));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("10.0.0.5");
    }

    @Test
    void openCircuit_is503() {
        CircuitBreaker open = CircuitBreaker.ofDefaults("t");
        open.transitionToOpenState();

        var exchange = handle(CallNotPermittedException.createCallNotPermittedException(open));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void timeout_is504() {
        assertThat(handle(new TimeoutException("slow")).getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void explicitResponseStatus_isKept() {
        assertThat(handle(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void unexpectedError_is500_withoutLeakingTheMessage() {
        var exchange = handle(new IllegalStateException("jdbc:secret@internal-host"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("secret");
    }
}
