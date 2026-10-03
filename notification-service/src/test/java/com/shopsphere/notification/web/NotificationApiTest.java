package com.shopsphere.notification.web;

import com.shopsphere.notification.config.SecurityConfig;
import com.shopsphere.notification.model.Notification;
import com.shopsphere.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class NotificationApiTest {

    @Autowired MockMvc mvc;
    @MockBean NotificationService service;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyTheCallersNotifications_neverTakingUserIdFromTheRequest() throws Exception {
        when(service.forUser("alice", 0, 20)).thenReturn(new PageImpl<>(List.of(
                new Notification("e1", "alice", "o-1", "ORDER_PLACED", "Thanks!", Instant.parse("2026-03-01T12:00:00Z"))),
                PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/notifications").param("userId", "bob").with(jwt().jwt(j -> j.subject("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].message").value("Thanks!"))
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(service).forUser("alice", 0, 20);       // "bob" in the query string was ignored
    }

    @Test
    void rejectsOversizedPages() throws Exception {
        mvc.perform(get("/api/v1/notifications").param("size", "10000").with(jwt())).andExpect(status().isBadRequest());
    }
}
