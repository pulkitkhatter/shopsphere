package com.shopsphere.auth.service;

import org.springframework.http.HttpStatus;

/** RFC 6749 section 5.2 error: {"error":"invalid_grant","error_description":"..."} */
public class OAuthException extends RuntimeException {
    private final String error;
    private final HttpStatus status;

    public OAuthException(String error, String description, HttpStatus status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public String getError() { return error; }
    public HttpStatus getStatus() { return status; }

    public static OAuthException invalidGrant(String description) {
        return new OAuthException("invalid_grant", description, HttpStatus.BAD_REQUEST);
    }

    public static OAuthException unsupportedGrant(String grantType) {
        return new OAuthException("unsupported_grant_type", "Unsupported grant_type: " + grantType, HttpStatus.BAD_REQUEST);
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description, HttpStatus.BAD_REQUEST);
    }
}
