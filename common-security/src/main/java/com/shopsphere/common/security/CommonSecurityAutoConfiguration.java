package com.shopsphere.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@AutoConfiguration
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class CommonSecurityAutoConfiguration {

    /**
     * Verifies RS256 signatures against the auth-service JWKS (keys are fetched lazily and cached,
     * so a service can start before the auth-service) and validates exp/nbf + issuer.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty("shopsphere.security.jwk-set-uri")
    JwtDecoder jwtDecoder(JwtSecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.getJwkSetUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.getIssuer()));
        return decoder;
    }
}
