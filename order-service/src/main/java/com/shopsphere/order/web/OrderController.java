package com.shopsphere.order.web;

import com.shopsphere.order.service.OrderPricingCalculator;
import com.shopsphere.order.service.OrderService;
import com.shopsphere.order.web.dto.OrderRequest;
import com.shopsphere.order.web.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Tag(name = "Orders")
@SecurityRequirement(name = "bearer-jwt")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Place an order for the authenticated user",
            description = "Send an Idempotency-Key header to make retries safe: a repeated key returns the original order (200) instead of creating a new one (201).")
    public ResponseEntity<OrderResponse> place(
            @Valid @RequestBody OrderRequest request,
            @Parameter(description = "Client generated unique key (UUID), 8-64 chars")
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(min = 8, max = 64) String idempotencyKey,
            Authentication auth) {

        List<OrderPricingCalculator.Line> lines = request.items().stream()
                .map(l -> new OrderPricingCalculator.Line(l.productId(), l.quantity())).toList();
        var placed = service.place(auth.getName(), lines, idempotencyKey);
        OrderResponse body = OrderResponse.from(placed.order());
        if (!placed.created()) {
            return ResponseEntity.ok(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @Operation(summary = "My orders (newest first)")
    public Map<String, Object> mine(@RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
                                    Authentication auth) {
        var result = service.mine(auth.getName(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return Map.of("content", result.map(OrderResponse::from).getContent(),
                "page", result.getNumber(), "size", result.getSize(),
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
    }

    @GetMapping("/all")
    @Operation(summary = "All orders (ADMIN)")
    public Map<String, Object> all(@RequestParam(defaultValue = "0") @Min(0) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = service.all(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return Map.of("content", result.map(OrderResponse::from).getContent(),
                "page", result.getNumber(), "size", result.getSize(),
                "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of my orders (admins can read any)")
    public OrderResponse get(@PathVariable String id, Authentication auth) {
        return OrderResponse.from(service.get(id, auth.getName(), isAdmin(auth)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order; stock is restored asynchronously via Kafka")
    public OrderResponse cancel(@PathVariable String id, Authentication auth) {
        return OrderResponse.from(service.cancel(id, auth.getName(), isAdmin(auth)));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
