package com.shopsphere.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * shopsphere.security.jwk-set-uri : where the auth-service publishes its public keys (JWKS)
 * shopsphere.security.issuer      : expected "iss" claim; tokens from any other issuer are rejected
 */
@ConfigurationProperties(prefix = "shopsphere.security")
public class JwtSecurityProperties {

    private String jwkSetUri;
    private String issuer = "http://localhost:8081";

    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
