package com.shopsphere.product.web;

import com.shopsphere.product.service.ProductData;
import com.shopsphere.product.service.ProductService;
import com.shopsphere.product.web.dto.ProductRequestV1;
import com.shopsphere.product.web.dto.ProductV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * DEPRECATED API version. Kept running so existing clients do not break; every response carries
 * Deprecation / Sunset / Link headers (RFC 8594) pointing consumers at v2.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products v1 (deprecated)")
public class ProductControllerV1 {

    static final String SUNSET = "Sat, 31 Oct 2026 23:59:59 GMT";

    private final ProductService service;

    public ProductControllerV1(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List products (max 100)", deprecated = true)
    public List<ProductV1> list(@RequestParam(required = false) String category, HttpServletResponse response) {
        deprecationHeaders(response);
        return service.listLegacy(category).stream().map(ProductV1::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one product", deprecated = true)
    public ProductV1 get(@PathVariable String id, HttpServletResponse response) {
        deprecationHeaders(response);
        return ProductV1.from(service.get(id));
    }

    @PostMapping
    @Operation(summary = "Create product (ADMIN)", deprecated = true, security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<ProductV1> create(@Valid @RequestBody ProductRequestV1 r, HttpServletResponse response) {
        deprecationHeaders(response);
        ProductV1 created = ProductV1.from(service.create(toData(r)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace product (ADMIN)", deprecated = true, security = @SecurityRequirement(name = "bearer-jwt"))
    public ProductV1 update(@PathVariable String id, @Valid @RequestBody ProductRequestV1 r, HttpServletResponse response) {
        deprecationHeaders(response);
        return ProductV1.from(service.update(id, toData(r)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete product (ADMIN)", deprecated = true, security = @SecurityRequirement(name = "bearer-jwt"))
    public void delete(@PathVariable String id, HttpServletResponse response) {
        deprecationHeaders(response);
        service.delete(id);
    }

    private static ProductData toData(ProductRequestV1 r) {
        return new ProductData(r.sku(), r.name(), r.description(), r.category(), r.price(), r.stock(), List.of());
    }

    private static void deprecationHeaders(HttpServletResponse response) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", SUNSET);
        response.setHeader("Link", "</api/v2/products>; rel=\"successor-version\"");
    }
}
