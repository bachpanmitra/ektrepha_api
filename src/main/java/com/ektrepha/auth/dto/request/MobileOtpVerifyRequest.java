package com.ektrepha.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MobileOtpVerifyRequest(
		@NotBlank String challengeId,
		@NotBlank String otp,
		// Optional app-generated device identifier (stable per install) — used only to check
		// FIRST5_FEE_WAIVER trial eligibility on a brand-new signup (com.ektrepha.trial). Older app
		// builds that omit this simply get no device-reuse signal checked.
		String deviceId) {
}
