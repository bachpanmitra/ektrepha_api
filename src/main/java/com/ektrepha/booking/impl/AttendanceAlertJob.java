package com.ektrepha.booking.impl;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.repository.BookingAttendanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Two independent sweeps over today's {@code booking_attendance} rows (API Map rows 42-43), each
 * idempotent via its own dedup timestamp column so a 5/15-minute re-run never double-alerts the
 * same row. "Alert ops" is a log line, not a real broadcast channel - same stand-in
 * {@code NannyAttendanceServiceImpl}'s OTP-lock alert and {@code VerificationExpiryAuditJob}'s PCC
 * reminder use, until a real ops-broadcast exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceAlertJob {

	private static final Duration LATE_CHECK_IN_GRACE = Duration.ofMinutes(10);
	private static final Duration FORGOT_CHECK_OUT_GRACE = Duration.ofMinutes(60);

	private final BookingAttendanceRepository bookingAttendanceRepository;
	private final PushSenderService pushSenderService;

	@Scheduled(cron = "${app.attendance.late-check-in-alert-cron:0 */5 * * * *}")
	public void lateCheckInSweep() {
		Instant cutoff = Instant.now().minus(LATE_CHECK_IN_GRACE);
		for (BookingAttendance row : bookingAttendanceRepository.findDueForLateCheckInAlert(cutoff)) {
			try {
				Booking booking = row.getBooking();
				pushSenderService.sendToUser(booking.getNanny().getUser().getId(),
						"You haven't checked in yet", "Your shift started a while ago - check in now with today's code.");
				log.warn("Late check-in: bookingAttendanceId={}, bookingId={}, nannyId={}", row.getId(), booking.getId(), booking.getNanny().getId());
				bookingAttendanceRepository.markLateCheckInAlertSent(row.getId());
			} catch (RuntimeException e) {
				log.error("Could not send late-check-in alert for bookingAttendanceId={}", row.getId(), e);
			}
		}
	}

	@Scheduled(cron = "${app.attendance.forgot-check-out-alert-cron:0 */15 * * * *}")
	public void forgotCheckOutSweep() {
		Instant cutoff = Instant.now().minus(FORGOT_CHECK_OUT_GRACE);
		for (BookingAttendance row : bookingAttendanceRepository.findDueForForgotCheckOutAlert(cutoff)) {
			try {
				Booking booking = row.getBooking();
				pushSenderService.sendToUser(booking.getNanny().getUser().getId(),
						"Did you forget to check out?", "Your shift ended a while ago - check out now with today's code.");
				pushSenderService.sendToUser(booking.getParent().getUser().getId(),
						"Still checked in", booking.getNanny().getFirstName() + " hasn't checked out yet - let us know if everything's okay.");
				log.warn("Forgot check-out: bookingAttendanceId={}, bookingId={}, nannyId={}", row.getId(), booking.getId(), booking.getNanny().getId());
				bookingAttendanceRepository.markForgotCheckOutAlertSent(row.getId());
			} catch (RuntimeException e) {
				log.error("Could not send forgot-check-out alert for bookingAttendanceId={}", row.getId(), e);
			}
		}
	}

}
