package com.shopsphere.order.web;

import com.shopsphere.common.web.ApiExceptionHandler;
import com.shopsphere.common.web.NotFoundException;
import com.shopsphere.common.web.ServiceUnavailableException;
import com.shopsphere.order.config.SecurityConfig;
import com.shopsphere.order.model.Order;
import com.shopsphere.order.model.OrderItem;
import com.shopsphere.order.service.InvalidOrderException;
import com.shopsphere.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class OrderApiTest {

    static final String BODY = """
            {"items":[{"productId":"abc123","quantity":2}]}""";

    @Autowired MockMvc mvc;
    @MockBean OrderService service;
    @MockBean JwtDecoder jwtDecoder;

    static RequestPostProcessor alice() {
        return jwt().jwt(j -> j.subject("alice")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    static Order order(String id) {
        Order o = new Order();
        o.setId(id);
        o.setUserId("alice");
        o.setTotal(new BigDecimal("20.00"));
        o.setCreatedAt(Instant.parse("2026-03-01T12:00:00Z"));
        o.setItems(List.of(new OrderItem("abc123", "SKU-1", "Thing", new BigDecimal("10.00"), 2)));
        return o;
    }

    @Test
    void everyEndpoint_requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders/o-1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/orders/o-1/cancel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void place_newOrder_returns201_withLocation_andUsesTokenSubjectAsOwner() throws Exception {
        when(service.place(eq("alice"), any(), eq(null))).thenReturn(new OrderService.PlacedOrder(order("o-1"), true));

        mvc.perform(post("/api/v1/orders").with(alice()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/v1/orders/o-1")))
                .andExpect(jsonPath("$.total").value(20.00))
                .andExpect(jsonPath("$.items[0].lineTotal").value(20.00));
    }

    @Test
    void place_replayedIdempotencyKey_returns200_notCreated() throws Exception {
        when(service.place(eq("alice"), any(), eq("key-12345"))).thenReturn(new OrderService.PlacedOrder(order("o-1"), false));

        mvc.perform(post("/api/v1/orders").with(alice()).header("Idempotency-Key", "key-12345")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("o-1"));
    }

    @Test
    void place_ignoresAnyClientSuppliedPrice() throws Exception {
        when(service.place(any(), any(), any())).thenReturn(new OrderService.PlacedOrder(order("o-1"), true));

        mvc.perform(post("/api/v1/orders").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"abc123","quantity":1,"price":0.01}],"total":0.01}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(20.00));
    }

    @Test
    void place_validation_rejectsEmptyOrder_badQuantity_andSuspiciousProductId() throws Exception {
        mvc.perform(post("/api/v1/orders").with(alice()).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"abc\",\"quantity\":0}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"../etc/passwd\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").with(alice()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"abc\",\"quantity\":101}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void place_insufficientStock_is422() throws Exception {
        when(service.place(any(), any(), any())).thenThrow(new InvalidOrderException("Not enough stock"));

        mvc.perform(post("/api/v1/orders").with(alice()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Not enough stock"));
    }

    @Test
    void place_whenCatalogueDown_is503() throws Exception {
        when(service.place(any(), any(), any())).thenThrow(new ServiceUnavailableException("catalogue down", new RuntimeException("boom")));

        mvc.perform(post("/api/v1/orders").with(alice()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void get_passesAdminFlagFromRole() throws Exception {
        when(service.get("o-1", "admin", true)).thenReturn(order("o-1"));
        when(service.get("o-1", "alice", false)).thenThrow(new NotFoundException("nope"));

        mvc.perform(get("/api/v1/orders/o-1").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/o-1").with(alice())).andExpect(status().isNotFound());
    }

    @Test
    void cancel_returnsUpdatedOrder() throws Exception {
        when(service.cancel("o-1", "alice", false)).thenReturn(order("o-1"));

        mvc.perform(post("/api/v1/orders/o-1/cancel").with(alice())).andExpect(status().isOk());
        verify(service).cancel("o-1", "alice", false);
    }

    @Test
    void listAll_isAdminOnly() throws Exception {
        mvc.perform(get("/api/v1/orders/all").with(alice())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void mine_rejectsPageSizeAbove50() throws Exception {
        mvc.perform(get("/api/v1/orders").with(alice()).param("size", "500")).andExpect(status().isBadRequest());
    }
}
