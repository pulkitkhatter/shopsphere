package com.shopsphere.auth.web;

import com.shopsphere.auth.service.OAuthException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OAuthExceptionHandler {

    @ExceptionHandler(OAuthException.class)
    ResponseEntity<Map<String, String>> handle(OAuthException e) {
        return ResponseEntity.status(e.getStatus())
                .header("Cache-Control", "no-store")
                .body(Map.of("error", e.getError(), "error_description", e.getMessage()));
    }
}
