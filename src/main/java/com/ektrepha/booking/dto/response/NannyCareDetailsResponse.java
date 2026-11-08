package com.ektrepha.booking.dto.response;

import java.util.List;

import com.ektrepha.parent.dto.response.AddressResponse;

/**
 * The assigned caregiver's "Care guide" screen (API Map rows 12-13) — everything the parent has
 * filled in that's actually backed by real data today: the child's allergies/care-notes, the
 * booking address (incl. {@code accessNotes}), and the parent's name/masked phone. Spec fields
 * with no backing data model yet (medicine schedule, doctor/clinic, diet specifics, screen-time
 * rules, pickup authorization, languages, pets, detailed house rules, a second contact) are
 * deliberately left out rather than sent as always-null placeholders — see the "Parent Details"
 * tab of the nanny-app spec sheet for the full target field list.
 */
public record NannyCareDetailsResponse(
		ChildSummary child,
		List<String> allergies,
		String medicalNotes,
		String routine,
		String comfort,
		String doNot,
		AddressResponse address,
		String parentName,
		String parentPhoneMasked) {
}
