package com.ektrepha.child.dto.response;

import java.util.List;

public record CareNotes(
		List<String> allergies,
		String medicalNotes,
		String routine,
		String comfort,
		String doNot) {
}
