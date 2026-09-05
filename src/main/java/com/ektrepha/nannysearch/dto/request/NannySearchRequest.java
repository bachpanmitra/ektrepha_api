package com.ektrepha.nannysearch.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * {@code parentAddressId}/{@code lat}/{@code lng} are all optional — resolution order is an
 * explicit lat/lng override, then a named saved address, then the parent's primary address.
 * {@code radiusKm} must be one of the allowed values in {@code app.search.allowed-radii-km} and
 * {@code windowEnd} must be after {@code windowStart} — both checked in the service layer, not
 * via bean validation, matching this codebase's existing convention of throwing domain
 * exceptions at the point of failure rather than declarative cross-field constraints.
 */
public record NannySearchRequest(
		Long parentAddressId,
		Double lat,
		Double lng,
		@NotNull Integer radiusKm,
		@NotNull Instant windowStart,
		@NotNull Instant windowEnd,
		@NotNull Long childId,
		BigDecimal minPrice,
		BigDecimal maxPrice,
		BigDecimal budget,
		Integer minYearsExperience,
		String educationLevel,
		List<Long> languageIds,
		List<Long> skillIds,
		Integer page,
		Integer pageSize) {
}
