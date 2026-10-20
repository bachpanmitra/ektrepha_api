package com.ektrepha.elder.dto.response;

import java.util.List;

public record ElderDetailResponse(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		Integer age,
		String gender,
		String relationship,
		String mobility,
		List<String> conditions,
		List<String> allergies,
		String medicationNotes,
		String preferredLanguage,
		String emergencyContactName,
		String emergencyContactPhone,
		String doctorName,
		String doctorPhone) {
}
