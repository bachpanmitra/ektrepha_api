package com.ektrepha.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@Valid @NotNull Jwt jwt,
		@Valid @NotNull Otp otp,
		@Valid @NotNull LoginLockout loginLockout,
		@Valid @NotNull Google google,
		@Valid @NotNull Aws aws,
		@Valid @NotNull Startup startup) {

	public record Jwt(
			@NotBlank @Size(min = 32, message = "must be at least 32 characters (256 bits) for HS256 signing") String secret,
			@NotNull @Positive Long accessTokenTtlMinutes,
			@NotNull @Positive Long refreshTokenTtlDays) {
	}

	public record Otp(
			@NotNull @Positive Long ttlMinutes,
			@NotNull @Positive Integer maxAttempts) {
	}

	public record LoginLockout(
			@NotNull @Positive Integer maxFailures,
			@NotNull @Positive Long lockoutMinutes) {
	}

	public record Google(
			@NotBlank(message = "app.google.client-id must be set — the OAuth client ID Google ID tokens are issued for") String clientId) {
	}

	public record Aws(@Valid @NotNull S3 s3) {

		public record S3(
				@NotBlank(message = "app.aws.s3.bucket must be set, even to a placeholder value if the S3 check is disabled") String bucket,
				@NotBlank(message = "app.aws.s3.region must be set, even to a placeholder value if the S3 check is disabled") String region) {
		}
	}

	public record Startup(
			@DefaultValue("false") boolean checkS3,
			@DefaultValue("false") boolean seedAdmin,
			String seedAdminEmail,
			String seedAdminPassword) {
	}
}
