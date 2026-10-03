package com.shopsphere.auth.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Only the SHA-256 hash of a refresh token is stored (like a password), so a DB leak does not leak sessions. */
@Document("refresh_tokens")
public class RefreshToken {

    @Id
    private String tokenHash;
    private String username;
    @Indexed(expireAfter = "0s")   // MongoDB TTL index: document is removed once expiresAt has passed
    private Instant expiresAt;

    public RefreshToken() {}

    public RefreshToken(String tokenHash, String username, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.username = username;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public String getUsername() { return username; }
    public Instant getExpiresAt() { return expiresAt; }
}
