package com.shopsphere.gateway.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeoutException;

/**
 * Turns every failure that happens while routing into a small RFC 7807 JSON document and never leaks internals:
 *  - no instance registered / connection refused / circuit open -> 503 (try again shortly)
 *  - downstream too slow                                           -> 504
 *  - anything else unexpected                                      -> 500 with a generic message (details only in the log)
 */
@Component
@Order(-2)       // before Spring Boot's DefaultErrorWebExceptionHandler (-1)
public class GatewayErrorHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);
    private final ObjectMapper mapper;

    public GatewayErrorHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatusCode status = statusOf(ex);
        if (status.is5xxServerError()) {
            log.warn("Gateway error for {} {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath(), ex.toString());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detailFor(status));
        problem.setTitle(titleFor(status));
        try {
            byte[] json = mapper.writeValueAsBytes(problem);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(json);
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(ex);
        }
    }

    static HttpStatusCode statusOf(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof NotFoundException nf) return nf.getStatusCode();                  // "Unable to find instance for X"
            if (t instanceof CallNotPermittedException) return HttpStatus.SERVICE_UNAVAILABLE;   // circuit breaker is open
            if (t instanceof ConnectException || t instanceof ClosedChannelException) return HttpStatus.SERVICE_UNAVAILABLE;
            if (t instanceof TimeoutException) return HttpStatus.GATEWAY_TIMEOUT;
            if (t instanceof ResponseStatusException rse) return rse.getStatusCode();
            t = t.getCause() == t ? null : t.getCause();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String titleFor(HttpStatusCode s) {
        return s.value() == 503 ? "Service unavailable" : s.value() == 504 ? "Gateway timeout"
                : s.is4xxClientError() ? "Request rejected" : "Internal error";
    }

    private static String detailFor(HttpStatusCode s) {
        return s.value() == 503 ? "The service is temporarily unavailable. Please retry in a moment."
                : s.value() == 504 ? "The service took too long to answer."
                : s.is4xxClientError() ? "The request could not be processed." : "Unexpected gateway error.";
    }
}
