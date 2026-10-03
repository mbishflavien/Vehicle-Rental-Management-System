package com.vrms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Interactive API documentation at /swagger-ui.html (spec at /v3/api-docs). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vrmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VRMS API")
                        .version("1.0")
                        .description("""
                                Vehicle Rental Management System REST API.

                                **Sign in:** `POST /api/auth/login` returns an OAuth2 bearer access token. Click
                                **Authorize** and paste it to call protected endpoints. Each endpoint requires a
                                permission (RBAC); see the role table in docs/03-architecture.md.""")
                        .contact(new Contact().name("MBISHIBISHI Flavien").email("flavmbish@gmail.com")))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token from POST /api/auth/login or the Google/GitHub sign-in")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
