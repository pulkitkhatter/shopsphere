package com.shopsphere.auth.web;

import com.shopsphere.auth.service.AuthService;
import com.shopsphere.auth.service.OAuthException;
import com.shopsphere.auth.service.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "OAuth2 token endpoint")
public class TokenController {

    private final AuthService auth;

    public TokenController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "OAuth2 token endpoint",
            description = "grant_type=password (username, password) or grant_type=refresh_token (refresh_token). "
                    + "Refresh tokens rotate: each one can be used once.")
    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<TokenResponse> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestParam(name = "refresh_token", required = false) String refreshToken) {

        TokenResponse response = switch (grantType) {
            case "password" -> {
                if (isBlank(username) || isBlank(password)) {
                    throw OAuthException.invalidRequest("username and password are required");
                }
                yield auth.passwordGrant(username, password);
            }
            case "refresh_token" -> {
                if (isBlank(refreshToken)) {
                    throw OAuthException.invalidRequest("refresh_token is required");
                }
                yield auth.refreshGrant(refreshToken);
            }
            default -> throw OAuthException.unsupportedGrant(grantType);
        };
        // RFC 6749 5.1: token responses must not be cached
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(response);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
