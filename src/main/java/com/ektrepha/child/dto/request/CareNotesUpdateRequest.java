package com.ektrepha.child.dto.request;

import java.util.List;

/** Allergies is the real {@code children.allergies} column (migration 026); the rest stays in {@code meta_data} JSONB. */
public record CareNotesUpdateRequest(
		List<String> allergies,
		String medicalNotes,
		String routine,
		String comfort,
		String doNot) {
}
