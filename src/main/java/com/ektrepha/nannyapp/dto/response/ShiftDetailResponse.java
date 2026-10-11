package com.ektrepha.nannyapp.dto.response;

import java.time.Instant;

/** Matches the nanny app's {@code ShiftDetail}. familyMaskedPhone is empty unless the booking is CONFIRMED/IN_PROGRESS - same rule as the parent side's contact endpoint. */
public record ShiftDetailResponse(
		String id, String date, String startTime, String endTime, String kind, String status,
		String areaLabel, String childLabel, String assignedByManagerName, long visitNumberToFamily,
		String addressLine1, String addressLine2, double distanceKm, String gateNote, String childName,
		int childAgeYears, String careNotes, double familyRating, String familyMaskedPhone, Instant checkInAt,
		Instant checkOutAt, String checkInCode, String checkOutCode, ShiftExtensionResponse extension) {
}
