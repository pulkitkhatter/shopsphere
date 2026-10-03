package com.shopsphere.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.time.Duration;

@Configuration
public class GatewayBeans {

    /** Anonymous endpoints (login, register) are limited per client IP. */
    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange));
    }

    /** Authenticated traffic is limited per user, so one noisy user cannot exhaust a shared office/NAT address. */
    @Bean
    @org.springframework.context.annotation.Primary   // default for the factory; routes pick explicitly by name
    KeyResolver principalOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal().map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> clientIp(exchange)));
    }

    /**
     * Uses the socket address only. X-Forwarded-For is client controlled and must be trusted only when the
     * gateway sits behind a known load balancer.
     */
    static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakerDefaults() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(8)).build())
                .build());
    }
}
