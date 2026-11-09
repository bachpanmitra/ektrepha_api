package com.ektrepha.admin.service;

import java.time.LocalDate;
import java.util.List;

import com.ektrepha.admin.dto.request.AdminBookingCancelRequest;
import com.ektrepha.admin.dto.request.ManualAttendanceRequest;
import com.ektrepha.admin.dto.response.AdminBookingCandidateResponse;
import com.ektrepha.admin.dto.response.AdminBookingDetailResponse;
import com.ektrepha.admin.dto.response.AdminBookingListResponse;
import com.ektrepha.admin.dto.response.AdminOrderActivityResponse;
import com.ektrepha.admin.dto.response.AdminTripStatusResponse;
import com.ektrepha.booking.dto.response.AttendanceDayEntry;
import com.ektrepha.booking.dto.response.AttendanceMonthResponse;
import com.ektrepha.model.BookingStatus;

public interface AdminBookingService {

	/** {@code from}/{@code to} are ISO local dates (e.g. "2026-09-30"), inclusive of {@code from} and exclusive of {@code to}, resolved in IST. */
	AdminBookingListResponse list(BookingStatus status, String from, String to, Long zoneAreaId, Long serviceTypeId, String q, int page, int size);

	AdminBookingDetailResponse detail(Long id);

	// Ops override-cancel — any status except already CANCELLED/COMPLETED, including mid-shift
	// IN_PROGRESS, unlike the parent-facing cancel which is PENDING/CONFIRMED only.
	AdminBookingDetailResponse cancel(Long adminUserId, Long bookingId, AdminBookingCancelRequest request);

	List<AdminBookingCandidateResponse> candidates(Long id);

	List<AdminOrderActivityResponse> activity(Long id);

	// Day-by-day attendance for ops (API Spec sheet row 2) - unlike the nanny/parent month view this
	// includes manualByUserId, since ops is exactly who's allowed to see which colleague recorded a
	// manual entry.
	AttendanceMonthResponse attendanceMonth(Long bookingId, String month);

	// Backup when the parent can't give the code - phone dead, not home (OTP Rules sheet rule 12).
	AttendanceDayEntry manualCheckIn(Long adminUserId, Long bookingId, LocalDate date, ManualAttendanceRequest request);

	AttendanceDayEntry manualCheckOut(Long adminUserId, Long bookingId, LocalDate date, ManualAttendanceRequest request);

	// Visibility into the nanny's "on the way"/arrived state for today — the parent and the nanny
	// already have their own equivalents (NannyTripService#parentEta / #nannyEta); ops had no way to
	// see this at all.
	AdminTripStatusResponse tripStatus(Long bookingId);

	// Ops override of the nanny's own start-care/complete-care actions (NannyBookingService), for
	// when the nanny can't or didn't tap it themselves (e.g. app issue, phone dead) — same
	// CONFIRMED->IN_PROGRESS->COMPLETED transitions, just admin-initiated and logged as such.
	AdminBookingDetailResponse start(Long adminUserId, Long bookingId);

	AdminBookingDetailResponse complete(Long adminUserId, Long bookingId);

}
