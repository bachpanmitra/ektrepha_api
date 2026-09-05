package com.ektrepha.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Binds to the same {@code spring.datasource.*} keys Boot's own
 * {@code DataSourceProperties} uses, purely to fail startup with a clear
 * message (instead of a raw placeholder-resolution error) when required
 * connection details are missing. Never injected anywhere directly — its
 * only job is to be validated during context refresh.
 */
@Validated
@ConfigurationProperties(prefix = "spring.datasource")
public record DatasourceValidationProperties(
		@NotBlank(message = "spring.datasource.url must be set (DB_URL env var in stage/prod; hardcoded in the dev profile)") String url,
		@NotBlank(message = "spring.datasource.username must be set (DB_USERNAME env var in stage/prod; 'postgres' in dev)") String username,
		@NotBlank(message = "spring.datasource.password must be set (DB_PASSWORD env var)") String password) {
}
