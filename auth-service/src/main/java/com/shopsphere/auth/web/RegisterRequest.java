package com.shopsphere.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 32) @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "only letters, digits, '_', '.', '-'")
        String username,
        @NotBlank @Email @Size(max = 120)
        String email,
        @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters")
        String password) {
}
