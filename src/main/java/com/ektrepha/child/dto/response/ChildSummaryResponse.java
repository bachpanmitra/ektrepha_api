package com.ektrepha.child.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ChildSummaryResponse(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		LocalDate dob,
		String ageDisplay,
		List<String> allergies,
		Instant lastCareAt) {
}
