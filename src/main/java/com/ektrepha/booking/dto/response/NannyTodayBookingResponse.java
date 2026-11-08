package com.ektrepha.booking.dto.response;

import java.time.Instant;

import com.ektrepha.parent.dto.response.AddressResponse;

/**
 * The assigned caregiver's "today's shift" card (ektrepha-nanny-ui's Home screen) — deliberately a
 * thin, nanny-app-specific view of a {@code Booking} rather than a reuse of {@link BookingDetailResponse}:
 * the nanny app has no concept of price/review/availableActions. {@code status} is the
 * {@code Booking.status} lifecycle (CONFIRMED/IN_PROGRESS/...) owned by {@code NannyBookingServiceImpl#startCare/completeCare};
 * {@code todayAttendance} is the separate, geofenced, OTP-gated daily check-in/check-out
 * ({@code booking_attendance}, see {@code NannyAttendanceServiceImpl}) — null until that day's row exists.
 * {@code parentPhoneMasked} mirrors the parent-facing contact endpoint's own gating (API Map row
 * 11): null unless {@code status} is CONFIRMED or IN_PROGRESS, never the full number.
 */
public record NannyTodayBookingResponse(
		Long id,
		String status,
		ChildSummary child,
		Instant startTime,
		Instant endTime,
		AddressResponse address,
		String careNotes,
		TodayAttendanceSummary todayAttendance,
		String parentPhoneMasked) {
}
