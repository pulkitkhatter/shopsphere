package com.shopsphere.notification.web;

import com.shopsphere.notification.model.Notification;
import com.shopsphere.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Validated
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearer-jwt")
public class NotificationController {

    public record NotificationView(String id, String orderId, String type, String message, Instant createdAt) {
        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getOrderId(), n.getType(), n.getMessage(), n.getCreatedAt());
        }
    }

    public record PageView(List<NotificationView> content, int page, int size, long totalElements, int totalPages) {}

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My notifications, newest first")
    public PageView mine(@RequestParam(defaultValue = "0") @Min(0) int page,
                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                         Authentication auth) {
        Page<Notification> result = service.forUser(auth.getName(), page, size);   // always the caller's own: no userId parameter to tamper with
        return new PageView(result.getContent().stream().map(NotificationView::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
