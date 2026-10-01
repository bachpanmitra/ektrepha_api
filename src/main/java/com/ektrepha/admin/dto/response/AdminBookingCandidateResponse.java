package com.ektrepha.admin.dto.response;

/**
 * One "Assign a nanny" candidate row.
 * <p>
 * {@code onTimeRate} is always null — there is no attendance/check-in tracking in the schema yet
 * (that lands with the Roster/Attendance phase); it is left in the shape rather than omitted so the
 * frontend doesn't need a second response type once it exists. {@code rating} is null when the
 * candidate has no reviews yet. {@code distanceKm} is null when the candidate has no declared
 * service-area location, or the booking's address has no lat/lng on file.
 */
public record AdminBookingCandidateResponse(
		Long nannyId,
		String name,
		Double distanceKm,
		double hoursBookedThisWeek,
		int previousVisitsToFamily,
		Double rating,
		Double onTimeRate,
		boolean isFree,
		String notFreeReason) {
}
