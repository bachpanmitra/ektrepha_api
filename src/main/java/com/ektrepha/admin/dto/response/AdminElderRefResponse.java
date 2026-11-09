package com.ektrepha.admin.dto.response;

import java.util.List;

/** Elder Care counterpart of {@link AdminChildRefResponse} — carries the care-relevant detail (conditions/allergies/medication/emergency contact) ops needs on a booking's detail view, unlike the child ref's bare id/name/age. */
public record AdminElderRefResponse(
		Long id,
		String firstName,
		Integer age,
		String relationship,
		String mobility,
		List<String> conditions,
		List<String> allergies,
		String medicationNotes,
		String emergencyContactName,
		String emergencyContactPhone) {
}
