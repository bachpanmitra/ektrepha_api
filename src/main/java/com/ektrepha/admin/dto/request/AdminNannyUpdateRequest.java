package com.ektrepha.admin.dto.request;

import java.math.BigDecimal;

/**
 * Every field is optional — only the ones present are changed. There is no "manager" or "salary"
 * field: no management-hierarchy or payroll data model exists yet (see docs); {@code hourlyRate} is
 * the one compensation field that actually exists on {@code nanny}.
 */
public record AdminNannyUpdateRequest(
		Boolean active,
		BigDecimal hourlyRate,
		String educationLevel,
		Integer yearsExperience) {
}
