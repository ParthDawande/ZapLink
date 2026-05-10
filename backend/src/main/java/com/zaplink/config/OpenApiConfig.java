package com.zaplink.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI zaplinkOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ZapLink API")
                        .description("""
                                URL shortening service with analytics, QR codes, and admin moderation.

                                All `/api/*` endpoints (except `/api/auth/register`, `/api/auth/login`, \
                                and `/api/health`) require a JWT Bearer token. \
                                Click **Authorize**, paste a token obtained from `POST /api/auth/login`, \
                                and all subsequent "Try it out" calls will include the header automatically.

                                Endpoints under `/api/admin/*` additionally require the `ADMIN` role.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ZapLink")
                                .url("https://github.com/ParthDawande/zaplink")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste a JWT obtained from POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
