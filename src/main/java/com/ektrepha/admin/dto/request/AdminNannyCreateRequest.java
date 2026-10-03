package com.ektrepha.admin.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

/**
 * HR/Ops provisions the account — there is no nanny self-signup (see {@code StaffAuthService}).
 * {@code phone} is how the nanny logs in (mobile OTP), so it's the one required contact field.
 * {@code zoneAreaId}/{@code serviceTypeId}/{@code ownRate} are optional — when all three are given,
 * an initial {@code caregiver_zone_mapping} row is created alongside the account.
 * <p>
 * {@code dob} is required (PRD: "Age 18+ enforced at signup") - {@code AdminNannyServiceImpl}
 * rejects a dob that doesn't clear the 18+ bar with a 400, same gate the DB's own
 * {@code nanny_dob_18_plus_check} constraint enforces.
 */
public record AdminNannyCreateRequest(
		@NotBlank String firstName,
		String lastName,
		@NotBlank String phone,
		String email,
		@NotNull @Past LocalDate dob,
		String educationLevel,
		Integer yearsExperience,
		BigDecimal hourlyRate,
		Long zoneAreaId,
		Long serviceTypeId,
		BigDecimal ownRate) {
}
