package com.ektrepha.admin.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code ratingAvg}/{@code reviewCount} come from real {@code review} rows. There is no
 * roster/attendance/leave/payslip data model yet (see docs) — this profile deliberately has no
 * fields for those; the admin UI's Roster tab instead reads this nanny's real bookings
 * ({@code AdminNannyBookingsResponse}), and Attendance/Leave/Payslips are separate "not built yet"
 * tabs with no backing endpoint.
 */
public record AdminNannyDetailResponse(
		Long id,
		String firstName,
		String lastName,
		String phone,
		String email,
		LocalDate dob,
		String bio,
		String educationLevel,
		Integer yearsExperience,
		BigDecimal hourlyRate,
		String verificationStatus,
		/** The reason behind the *current* status - see {@code AdminNannyStatusHistoryResponse} for the full trail. */
		String statusReason,
		String statusChangedByName,
		Instant statusChangedAt,
		boolean active,
		Double ratingAvg,
		long reviewCount,
		List<AdminNannyZoneMappingResponse> zoneMappings,
		Instant createdAt) {
}
