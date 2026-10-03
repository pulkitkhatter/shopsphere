package com.shopsphere.product.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** v2 adds tags to the write model. */
public record ProductRequestV2(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+", message = "letters, digits and '-' only") String sku,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 60) String category,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Min(0) int stock,
        @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags) {
}
