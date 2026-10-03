package com.shopsphere.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/** Runs the real gateway filter chain with service discovery switched off. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.loadbalancer.enabled=false",
        "shopsphere.gateway.allowed-origins=http://localhost:3000"
})
class GatewaySecurityTest {

    @Autowired WebTestClient client;
    @org.springframework.boot.test.mock.mockito.MockBean ReactiveJwtDecoder decoder;

    @Test
    void protectedRoutes_withoutToken_are401_andNeverReachAService() {
        client.get().uri("/api/v1/orders").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/notifications").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/v2/products").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void protectedRoute_withValidToken_passesSecurity() {
        client.mutateWith(mockJwt().jwt(j -> j.subject("alice")))
                .get().uri("/api/v1/orders").exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));   // 5xx: no backend in this test
    }

    @Test
    void garbageToken_is401() {
        org.mockito.Mockito.when(decoder.decode("garbage")).thenReturn(reactor.core.publisher.Mono.error(
                new org.springframework.security.oauth2.jwt.BadJwtException("bad")));
        client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer garbage").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void publicCatalogueAndLogin_areNotBlockedBySecurity() {
        client.get().uri("/api/v2/products").exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
        client.post().uri("/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).bodyValue("grant_type=password").exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
    }

    @Test
    void unknownPaths_requireAuthentication_soTheGatewayLeaksNothingAboutInternalLayout() {
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
        client.get().uri("/internal/anything").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void corsPreflight_fromAllowedOrigin_isAccepted_andFromOtherOrigin_isRejected() {
        client.options().uri("http://localhost:8080/api/v1/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");

        client.options().uri("http://localhost:8080/api/v1/orders")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void everyResponse_carriesACorrelationId_andAGoodClientIdIsPreserved() {
        client.get().uri("/api/v1/orders").exchange().expectHeader().exists("X-Correlation-Id");

        client.get().uri("/api/v1/orders").header("X-Correlation-Id", "trace-12345678").exchange()
                .expectHeader().valueEquals("X-Correlation-Id", "trace-12345678");

        // a malicious value (log forging / header injection attempt) is replaced, not echoed
        client.get().uri("/api/v1/orders").header("X-Correlation-Id", "x y\\nfake-log-line").exchange()
                .expectHeader().value("X-Correlation-Id", v -> assertThat(v).matches("[A-Za-z0-9-]{8,64}"));
    }
}
