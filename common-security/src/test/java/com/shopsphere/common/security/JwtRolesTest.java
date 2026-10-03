package com.shopsphere.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRolesTest {

    private static Jwt jwt(Object roles) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("bob")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (roles != null) b.claim("roles", roles);
        return b.build();
    }

    @Test
    void rolesClaimBecomesRolePrefixedAuthorities() {
        var auth = JwtRoles.converter().convert(jwt(List.of("ADMIN", "USER")));

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        assertThat(auth.getName()).isEqualTo("bob");
    }

    @Test
    void tokenWithoutRoles_hasNoAuthorities() {
        assertThat(JwtRoles.converter().convert(jwt(null)).getAuthorities()).isEmpty();
    }
}
