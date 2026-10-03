package com.shopsphere.product.web;

import com.shopsphere.product.repo.ProductSearchCriteria;
import com.shopsphere.product.service.ProductData;
import com.shopsphere.product.service.ProductService;
import com.shopsphere.product.web.dto.PageResponse;
import com.shopsphere.product.web.dto.ProductRequestV2;
import com.shopsphere.product.web.dto.ProductV2;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/products")
@Validated
@Tag(name = "Products v2")
public class ProductControllerV2 {

    /** Only whitelisted fields can be sorted on: arbitrary sort keys would allow unindexed, expensive sorts. */
    private static final Set<String> SORTABLE = Set.of("name", "price", "createdAt");

    private final ProductService service;

    public ProductControllerV2(ProductService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search products with filters, sorting and pagination",
            description = "q = full-text (whole words) over name/description. sort = field,direction e.g. price,asc.")
    public PageResponse<ProductV2> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @Min(0) BigDecimal minPrice,
            @RequestParam(required = false) @Min(0) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc") String sort) {

        PageRequest pageable = PageRequest.of(page, size, parseSort(sort));
        var criteria = new ProductSearchCriteria(q, category, minPrice, maxPrice, inStock);
        return PageResponse.of(service.search(criteria, pageable), ProductV2::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one product (cached)")
    public ProductV2 get(@PathVariable String id) {
        return ProductV2.from(service.get(id));
    }

    @PostMapping
    @Operation(summary = "Create product (ADMIN)", security = @SecurityRequirement(name = "bearer-jwt"))
    public ResponseEntity<ProductV2> create(@Valid @RequestBody ProductRequestV2 r) {
        ProductV2 created = ProductV2.from(service.create(toData(r)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace product (ADMIN)", security = @SecurityRequirement(name = "bearer-jwt"))
    public ProductV2 update(@PathVariable String id, @Valid @RequestBody ProductRequestV2 r) {
        return ProductV2.from(service.update(id, toData(r)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete product (ADMIN)", security = @SecurityRequirement(name = "bearer-jwt"))
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw new IllegalArgumentException("Cannot sort by " + field);
        }
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }

    private static ProductData toData(ProductRequestV2 r) {
        return new ProductData(r.sku(), r.name(), r.description(), r.category(), r.price(), r.stock(),
                r.tags() == null ? List.of() : r.tags());
    }
}
