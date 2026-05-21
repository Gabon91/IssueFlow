package com.att.tdp.issueflow.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the {@code bearerAuth} security scheme on the springdoc-generated OpenAPI document
 * so the Swagger UI "Authorize" button can capture a JWT and attach it to every "Try it out"
 * request. Public endpoints (e.g. {@code /auth/login}) override this with their own
 * {@code @SecurityRequirements} annotation if needed.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI issueFlowOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("IssueFlow API")
                .description("Ticket management backend platform — TDP 2026 Home Assignment.")
                .version("1.0.0"))
            .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
            .components(new Components().addSecuritySchemes(SCHEME_NAME,
                new SecurityScheme()
                    .name(SCHEME_NAME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT issued by POST /auth/login. Send as Authorization: Bearer <token>.")));
    }
}
