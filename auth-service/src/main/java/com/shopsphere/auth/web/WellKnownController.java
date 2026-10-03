package com.shopsphere.auth.web;

import com.nimbusds.jose.jwk.RSAKey;
import com.shopsphere.auth.config.AuthProperties;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Publishes the public signing key (JWKS) and an OpenID Connect discovery document. */
@RestController
@Hidden
public class WellKnownController {

    private final RSAKey rsaKey;
    private final AuthProperties props;

    public WellKnownController(RSAKey rsaKey, AuthProperties props) {
        this.rsaKey = rsaKey;
        this.props = props;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(rsaKey.toPublicJWK().toJSONObject()));
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> discovery() {
        String iss = props.issuer();
        return Map.of(
                "issuer", iss,
                "token_endpoint", iss + "/oauth/token",
                "userinfo_endpoint", iss + "/userinfo",
                "jwks_uri", iss + "/.well-known/jwks.json",
                "grant_types_supported", List.of("password", "refresh_token"),
                "response_types_supported", List.of("token"),
                "subject_types_supported", List.of("public"),
                "id_token_signing_alg_values_supported", List.of("RS256"));
    }
}
