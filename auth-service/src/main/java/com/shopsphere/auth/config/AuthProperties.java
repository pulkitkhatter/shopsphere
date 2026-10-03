package com.shopsphere.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "shopsphere.auth")
public record AuthProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Seed seed) {

    public AuthProperties {
        if (issuer == null) issuer = "http://localhost:8081";
        if (accessTokenTtl == null) accessTokenTtl = Duration.ofMinutes(15);
        if (refreshTokenTtl == null) refreshTokenTtl = Duration.ofDays(7);
        if (seed == null) seed = new Seed(false, null, null);
    }

    /** Development-only demo accounts. Disabled unless explicitly enabled. */
    public record Seed(boolean enabled, String adminPassword, String userPassword) {}
}
