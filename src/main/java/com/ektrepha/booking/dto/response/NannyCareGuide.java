package com.ektrepha.booking.dto.response;

import java.util.List;

/**
 * What the assigned caregiver needs to know about the person they look after, read from the
 * family's own profile data: a child's care notes (children.meta_data) or an elder's profile and
 * ElderCarePreferences (elders.meta_data). Fields the family hasn't filled in are null/empty; the
 * child-only and elder-only groups are null on the other vertical.
 */
public record NannyCareGuide(
		List<String> allergies,
		// Child care notes
		String medicalNotes,
		String routine,
		String comfort,
		String doNot,
		// Elder profile
		List<String> conditions,
		String mobility,
		String medicationNotes,
		String preferredLanguage,
		String foodType,
		List<String> dietNeeds,
		String foodsToAvoid,
		String whereThingsKept,
		List<String> homeSafety,
		// Emergency - elder profile only; a child has no doctor/hospital fields yet
		String doctorName,
		String doctorPhone,
		String preferredHospital,
		String emergencyContactName,
		String emergencyContactPhone,
		String howToGetIn) {
}
