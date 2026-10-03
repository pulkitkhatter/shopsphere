package com.shopsphere.auth.service;

import com.shopsphere.auth.config.AuthProperties;
import com.shopsphere.auth.model.RefreshToken;
import com.shopsphere.auth.model.User;
import com.shopsphere.auth.repo.RefreshTokenRepository;
import com.nimbusds.jose.JWSAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuthProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, AuthProperties props, Clock clock) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.props = props;
        this.clock = clock;
    }

    public TokenResponse issue(User user) {
        Instant now = clock.instant();
        List<String> roles = user.getRoles().stream().sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .claim("email", user.getEmail())
                .claim("scope", "openid profile")
                .build();
        JwsHeader header = JwsHeader.with(() -> JWSAlgorithm.RS256.getName()).build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        String refresh = newOpaqueToken();
        refreshTokens.save(new RefreshToken(sha256(refresh), user.getUsername(), now.plus(props.refreshTokenTtl())));
        return new TokenResponse(accessToken, "Bearer", props.accessTokenTtl().toSeconds(), refresh, "openid profile");
    }

    /**
     * Refresh-token rotation: a refresh token is single-use. It is deleted when redeemed and a new one is issued,
     * so a stolen token that was already used cannot be replayed.
     */
    public String consumeRefreshToken(String rawToken) {
        RefreshToken stored = refreshTokens.findById(sha256(rawToken))
                .orElseThrow(() -> OAuthException.invalidGrant("Refresh token is invalid or already used"));
        refreshTokens.deleteById(stored.getTokenHash());
        if (stored.getExpiresAt().isBefore(clock.instant())) {
            throw OAuthException.invalidGrant("Refresh token expired");
        }
        return stored.getUsername();
    }

    public void revokeAll(String username) {
        refreshTokens.deleteByUsername(username);
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32];     // 256 bits of entropy
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
