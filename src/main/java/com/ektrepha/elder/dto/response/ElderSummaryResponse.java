package com.ektrepha.elder.dto.response;

import java.util.List;

public record ElderSummaryResponse(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		Integer age,
		String gender,
		String relationship,
		String mobility,
		List<String> conditions) {
}
