package com.ektrepha.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Live, always-in-sync API contract for the frontend team (ektrepha-ui) — generated from the
 * actual controllers/DTOs rather than hand-maintained, so it can never drift the way a static doc
 * would. Served at {@code /swagger-ui/index.html} (interactive) and {@code /v3/api-docs} (raw
 * OpenAPI JSON, suitable for client codegen). See {@code docs/API_REFERENCE.md} for the readable,
 * runs-without-a-server companion.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	OpenAPI ektrephaOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Ektrepha Parent App API")
						.version("v1")
						.description("Account, Parent Profile, Child Profiles, Bookings, and Nanny Profile/Verification — "
								+ "the parent-facing API surface for the Ektrepha nanny marketplace. "
								+ "Auth: obtain a token via POST /api/v1/auth/login/email (or signup/phone/google), "
								+ "then click Authorize below and paste the accessToken."))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}

}
