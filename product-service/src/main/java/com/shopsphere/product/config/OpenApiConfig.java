package com.shopsphere.product.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "ShopSphere Product API", version = "2",
        description = "Catalogue API. v1 is deprecated (sunset announced via the Sunset header); use v2."))
@SecurityScheme(name = "bearer-jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    GroupedOpenApi v2() {
        return GroupedOpenApi.builder().group("v2").pathsToMatch("/api/v2/**").build();
    }

    @Bean
    GroupedOpenApi v1() {
        return GroupedOpenApi.builder().group("v1-deprecated").pathsToMatch("/api/v1/**").build();
    }
}
