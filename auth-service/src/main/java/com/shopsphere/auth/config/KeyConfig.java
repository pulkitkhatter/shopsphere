package com.shopsphere.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Asymmetric signing: only this service holds the private key; every other service verifies with the public key
 * published at /.well-known/jwks.json. A 2048-bit key is generated at start-up. For production load it from a
 * KMS/HSM/secret store and rotate it by publishing several keys (kid) in the JWKS.
 */
@Configuration
public class KeyConfig {

    @Bean
    RSAKey rsaKey() throws Exception {
        return new RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())
                .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .generate();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }

    /** Used by /userinfo so that this service also validates tokens (against its own public key). */
    @Bean
    JwtDecoder jwtDecoder(RSAKey rsaKey) throws Exception {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) rsaKey.toPublicKey()).build();
    }
}
