package com.shopsphere.product.config;

import com.shopsphere.common.security.JwtRoles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/*/products", "/api/*/products/**").permitAll()   // public catalogue
                .requestMatchers("/api/*/products/**").hasRole("ADMIN")                                // writes: admins only
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/**", "/v3/api-docs.yaml/**", "/swagger-ui/**", "/swagger-ui.html", "/docs/**").permitAll()
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(JwtRoles.converter())));
        return http.build();
    }
}
