package com.ektrepha.admin.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/**
 * Every field is optional — only the ones present are changed. There is no "manager" or "salary"
 * field: no management-hierarchy or payroll data model exists yet (see docs); {@code hourlyRate} is
 * the one compensation field that actually exists on {@code nanny}. {@code hourlyRate}'s
 * constraints are skipped when null (optional field) and otherwise match the column's own
 * {@code precision = 10, scale = 2} (see {@code Nanny#getHourlyRate}).
 */
public record AdminNannyUpdateRequest(
		Boolean active,
		@DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal hourlyRate,
		String educationLevel,
		Integer yearsExperience) {
}
