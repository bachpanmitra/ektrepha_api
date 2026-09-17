package com.ektrepha.child.dto.response;

import java.time.LocalDate;

public record ChildDetailResponse(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		LocalDate dob,
		String ageDisplay,
		String gender,
		CareNotes careNotes,
		int guardianCount,
		String relationship,
		boolean primaryContact) {
}
