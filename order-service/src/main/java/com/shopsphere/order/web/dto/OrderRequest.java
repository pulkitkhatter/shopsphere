package com.shopsphere.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Note: there is deliberately no price field. The server looks prices up itself. */
public record OrderRequest(@NotEmpty @Size(max = 50) List<@Valid Line> items) {

    public record Line(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,64}", message = "invalid product id") String productId,
            @Min(1) @Max(100) int quantity) {}
}
