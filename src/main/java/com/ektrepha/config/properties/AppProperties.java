package com.ektrepha.config.properties;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@Valid @NotNull Jwt jwt,
		@Valid @NotNull Otp otp,
		@Valid @NotNull LoginLockout loginLockout,
		@Valid @NotNull RateLimit rateLimit,
		@Valid @NotNull Google google,
		@Valid @NotNull Aws aws,
		@Valid @NotNull Startup startup,
		@Valid @NotNull Search search,
		@Valid @NotNull Pricing pricing,
		@Valid @NotNull Serviceability serviceability,
		@Valid @NotNull Email email,
		@Valid @NotNull Firebase firebase,
		@Valid @NotNull Geocoding geocoding,
		@Valid @NotNull MobileOtp mobileOtp,
		@Valid @NotNull Sms sms,
		@Valid @NotNull OlaMaps olaMaps) {

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

	/** Per-client-IP token bucket. See {@code RateLimiterServiceImpl}. */
	public record RateLimit(
			@DefaultValue("true") boolean enabled,
			@NotNull @Positive Integer capacity,
			@NotNull @Positive Long windowSeconds) {
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

	public record Search(
			@NotNull @NotEmpty List<Integer> allowedRadiiKm,
			@NotNull @Positive Integer defaultPageSize,
			@NotNull @Positive Integer candidateFetchLimit) {
	}

	public record Pricing(
			@NotNull @Positive BigDecimal combinedMultiplierCap) {
	}

	public record Serviceability(
			@NotNull @Positive Double maxZoneMatchKm) {
	}

	public record Email(@Valid @NotNull Brevo brevo) {

		public record Brevo(
				@NotBlank(message = "app.email.brevo.api-key must be set — the Brevo transactional API key") String apiKey,
				@NotBlank(message = "app.email.brevo.sender-email must be set — the verified Brevo sender address") String senderEmail,
				@NotBlank(message = "app.email.brevo.sender-name must be set") String senderName) {
		}
	}

	public record Firebase(
			@NotBlank(message = "app.firebase.service-account-path must be set — path to the Firebase service account JSON key") String serviceAccountPath) {
	}

	public record Geocoding(@Valid @NotNull Nominatim nominatim) {

		/** OpenStreetMap's free public Nominatim API — no API key, but usage-policy limits apply (see NominatimGeocodingProvider). */
		public record Nominatim(
				@NotBlank String baseUrl,
				@NotBlank(message = "app.geocoding.nominatim.user-agent must identify this app per Nominatim's usage policy") String userAgent,
				@NotNull @Positive Long minIntervalMillis,
				// Nominatim's countrycodes filter (comma-separated ISO 3166-1 alpha-2, e.g. "in") — every
				// zone this app serves is in India, so unrestricted global search lets an ambiguous
				// short query (e.g. "AECS") match an unrelated place on the other side of the world.
				@NotBlank String countryCodes) {
		}
	}

	/** Mobile-number OTP login/auto-signup — see MobileOtpServiceImpl. Reuses app.otp.{ttl-minutes,max-attempts} for the challenge's own expiry/attempt-limit. */
	public record MobileOtp(
			@NotNull @Positive Integer resendCooldownSeconds,
			@NotNull @Positive Integer requestLimitPerNumber,
			@NotNull @Positive Long requestLimitWindowMinutes,
			@NotNull @Positive Integer requestLimitPerIp,
			@NotNull @Positive Long requestLimitPerIpWindowMinutes,
			@NotNull @Positive Integer sessionHours,
			@Valid @NotNull FixedOtp fixedOtp,
			@Valid @NotNull ReviewAccount reviewAccount) {

		/**
		 * Dev/stage-only fixed-code bypass for allowlisted test numbers, so QA/CI never need a real
		 * SMS provider. Shows the client a "test mode" notice instead of claiming an SMS was sent.
		 * {@link com.ektrepha.startup.MobileOtpFixedCodeGuard} fails startup if this is ever enabled
		 * while the active profile is prod — unlike {@link ReviewAccount}, this is never meant to run
		 * there.
		 */
		public record FixedOtp(
				@DefaultValue("false") boolean enabled,
				String code,
				List<String> allowedNumbers) {
		}

		/**
		 * A narrow, prod-safe fixed-code bypass for a handful of explicitly allowlisted numbers —
		 * e.g. an App Store/Play Store reviewer account that can't receive real SMS. Deliberately
		 * separate from {@link FixedOtp}: this is allowed in prod (env-var controlled, off by
		 * default), but {@link com.ektrepha.startup.MobileOtpFixedCodeGuard} still fails startup if
		 * it's enabled with no code or an empty/oversized allowlist, and every boot with it active
		 * logs a warning naming the exact numbers, in every profile including prod. Unlike
		 * {@link FixedOtp}, the client sees no "test mode" notice — indistinguishable from a real
		 * send, since a reviewer follows the same UI a real user would.
		 */
		public record ReviewAccount(
				@DefaultValue("false") boolean enabled,
				String code,
				@Size(max = 3, message = "app.mobile-otp.review-account.allowed-numbers must stay small (max 3) — this bypass is for a handful of reviewer/test accounts, not general testing") List<String> allowedNumbers) {
		}
	}

	public record Sms(@Valid @NotNull Msg91 msg91) {

		/** MSG91 Flow API (https://control.msg91.com/api/v5/flow) — see AbstractSmsSender. */
		public record Msg91(
				@NotBlank(message = "app.sms.msg91.auth-key must be set — the MSG91 API auth key") String authKey,
				@NotBlank(message = "app.sms.msg91.template-id must be set — the DLT-approved MSG91 flow template id for OTP messages") String templateId,
				@NotBlank(message = "app.sms.msg91.otp-variable-name must be set — must match the template's variable name for the OTP value") String otpVariableName,
				String senderId) {
		}
	}

	/**
	 * Ola Maps (https://maps.olakrutrim.com) — reverse-geocode/autocomplete/place-details behind
	 * {@code com.ektrepha.location}. {@code apiKey} may be blank in dev/stage (see
	 * {@code NoopOlaMapsClient}, which is registered instead of the real client when it is); every
	 * other environment should set a real one. Each {@code dailyLimit} is a free-tier usage budget —
	 * tune these to the account's actual current Ola Maps plan allowance, tracked separately per API
	 * via {@code RateLimiterService} (see {@code LocationServiceImpl}).
	 */
	public record OlaMaps(
			String apiKey,
			@NotBlank String baseUrl,
			@Valid @NotNull Budget reverseGeocode,
			@Valid @NotNull Budget autocomplete,
			@Valid @NotNull Budget placeDetails) {

		public record Budget(@NotNull @Positive Integer dailyLimit) {
		}
	}
}
