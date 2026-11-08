package com.ektrepha.workforce.dto.request;

import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** One "on the way" GPS ping (API Map row 51) — sent automatically every 20-30s by the app while a trip is ON_THE_WAY. */
public record LocationPingRequest(
		@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
		Double accuracyM,
		@NotNull Instant recordedAt) {
}
