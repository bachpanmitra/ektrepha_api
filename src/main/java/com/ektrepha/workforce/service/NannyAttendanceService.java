package com.ektrepha.workforce.service;

import com.ektrepha.booking.dto.response.AttendanceMonthResponse;
import com.ektrepha.workforce.dto.response.AttendanceResponse;

/**
 * Nanny-driven, OTP-gated daily check-in/check-out (nanny app spec: Ektrepha_Nanny_API_Map.xlsx,
 * "OTP Rules"/"API Spec" sheets) — independent of {@code Booking.status}, which the separate
 * start/complete-care lifecycle ({@code NannyBookingServiceImpl}) owns. Backed by
 * {@code booking_attendance} (migration 047), one row per booking per day, rather than
 * {@code Booking.checkedInAt/checkedOutAt} (migration 037), which this stops writing to.
 */
public interface NannyAttendanceService {

	AttendanceResponse checkIn(Long callerUserId, Long bookingId, String otp, double lat, double lng);

	AttendanceResponse checkOut(Long callerUserId, Long bookingId, String otp, double lat, double lng);

	AttendanceMonthResponse month(Long callerUserId, Long bookingId, String month);

}
