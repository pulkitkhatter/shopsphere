package com.shopsphere.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "shopsphere.gateway")
public record GatewayProperties(List<String> allowedOrigins, String jwkSetUri, String issuer) {
    public GatewayProperties {
        if (allowedOrigins == null) allowedOrigins = List.of("http://localhost:3000");
        if (jwkSetUri == null) jwkSetUri = "http://localhost:8081/.well-known/jwks.json";
        if (issuer == null) issuer = "http://localhost:8081";
    }
}
