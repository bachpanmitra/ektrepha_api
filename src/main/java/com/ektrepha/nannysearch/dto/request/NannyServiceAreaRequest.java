package com.ektrepha.nannysearch.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** {@code radiusKm} defaults to 10 (matching the schema default) when omitted — applied in the service layer, not here. */
public record NannyServiceAreaRequest(
		@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
		@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
		@Positive Integer radiusKm) {
}
