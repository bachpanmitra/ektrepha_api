package com.ektrepha.workforce.service;

import com.ektrepha.workforce.dto.response.AttendanceResponse;

/**
 * Nanny-driven check-in/check-out timestamps — independent of {@code Booking.status}, which the
 * separate start/complete-care lifecycle (NannyBookingServiceImpl) owns. This is purely "what time
 * did the nanny actually arrive/leave", for the admin Attendance view and the dashboard's
 * lateOrNoCheckIn KPI; it applies to every booking occurrence the same way regardless of
 * {@code frequency} (ONE_TIME or one occurrence of a recurring series), since each occurrence is
 * its own row.
 */
public interface NannyAttendanceService {

	AttendanceResponse checkIn(Long callerUserId, Long bookingId);

	AttendanceResponse checkOut(Long callerUserId, Long bookingId);

}
