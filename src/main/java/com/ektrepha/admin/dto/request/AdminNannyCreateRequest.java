package com.ektrepha.admin.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

/**
 * HR/Ops provisions the account — there is no nanny self-signup (see {@code StaffAuthService}).
 * {@code phone} is how the nanny logs in (mobile OTP), so it's the one required contact field.
 * {@code zoneAreaId}/{@code serviceTypeId}/{@code ownRate} are optional — when all three are given,
 * an initial {@code caregiver_zone_mapping} row is created alongside the account.
 */
public record AdminNannyCreateRequest(
		@NotBlank String firstName,
		String lastName,
		@NotBlank String phone,
		String email,
		String educationLevel,
		Integer yearsExperience,
		BigDecimal hourlyRate,
		Long zoneAreaId,
		Long serviceTypeId,
		BigDecimal ownRate) {
}
