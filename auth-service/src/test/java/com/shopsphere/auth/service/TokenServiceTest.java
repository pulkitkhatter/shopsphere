package com.shopsphere.auth.service;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.shopsphere.auth.config.AuthProperties;
import com.shopsphere.auth.model.RefreshToken;
import com.shopsphere.auth.model.User;
import com.shopsphere.auth.repo.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock RefreshTokenRepository refreshTokens;

    final Instant now = Instant.parse("2026-01-01T10:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final AuthProperties props = new AuthProperties("http://issuer.test", Duration.ofMinutes(15), Duration.ofDays(7), null);
    TokenService service;
    NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("k1").generate();
        service = new TokenService(new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key))),
                refreshTokens, props, clock);
        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) key.toPublicKey()).build();
        decoder.setJwtValidator(token -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
    }

    @Test
    void issue_producesSignedJwtWithExpectedClaims() {
        User user = new User("bob", "b@x.io", "hash", Set.of("USER", "ADMIN"));

        TokenResponse response = service.issue(user);

        Jwt jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("bob");
        assertThat(jwt.getIssuer().toString()).isEqualTo("http://issuer.test");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void issue_storesOnlyHashOfRefreshToken() {
        User user = new User("bob", "b@x.io", "hash", Set.of("USER"));

        TokenResponse response = service.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(TokenService.sha256(response.refreshToken()));
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(response.refreshToken());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
    }

    @Test
    void consumeRefreshToken_isSingleUse() {
        String hash = TokenService.sha256("raw");
        when(refreshTokens.findById(hash)).thenReturn(Optional.of(new RefreshToken(hash, "bob", now.plusSeconds(60))));

        assertThat(service.consumeRefreshToken("raw")).isEqualTo("bob");
        verify(refreshTokens).deleteById(hash);
    }

    @Test
    void consumeRefreshToken_unknownToken_isInvalidGrant() {
        when(refreshTokens.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeRefreshToken("nope")).isInstanceOf(OAuthException.class);
    }

    @Test
    void consumeRefreshToken_expiredToken_isInvalidGrant() {
        String hash = TokenService.sha256("old");
        when(refreshTokens.findById(hash)).thenReturn(Optional.of(new RefreshToken(hash, "bob", now.minusSeconds(1))));

        assertThatThrownBy(() -> service.consumeRefreshToken("old"))
                .isInstanceOfSatisfying(OAuthException.class, e -> assertThat(e.getMessage()).contains("expired"));
    }
}
