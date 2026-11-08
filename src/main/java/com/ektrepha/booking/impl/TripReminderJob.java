package com.ektrepha.booking.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.repository.BookingAttendanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pushes both the nanny and the parent ~15 minutes before a shift starts, so neither is caught
 * off guard and the nanny has a nudge to tap "Start trip" (API Map row 64's same-day sweep — the
 * spec's second, evening-before sweep for tomorrow's bookings is deliberately not built yet, see
 * {@code BookingAttendance#reminderSentAt}'s javadoc). Never puts the plain check-in code in the
 * push text — same rule as every other nanny-app push.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripReminderJob {

	private static final long WINDOW_START_MINUTES = 10;
	private static final long WINDOW_END_MINUTES = 20;
	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

	private final BookingAttendanceRepository bookingAttendanceRepository;
	private final PushSenderService pushSenderService;

	@Scheduled(cron = "${app.booking.trip-reminder-cron:0 */5 * * * *}")
	public void sendDueReminders() {
		Instant now = Instant.now();
		Instant windowStart = now.plusSeconds(WINDOW_START_MINUTES * 60);
		Instant windowEnd = now.plusSeconds(WINDOW_END_MINUTES * 60);

		for (BookingAttendance row : bookingAttendanceRepository.findDueForStartReminder(windowStart, windowEnd)) {
			try {
				Booking booking = row.getBooking();
				pushSenderService.sendToUser(booking.getParent().getUser().getId(), "Your nanny is coming soon",
						booking.getNanny().getFirstName() + " is coming at " + formattedTime(booking) + ". Today's check-in code is in the app.");
				pushSenderService.sendToUser(booking.getNanny().getUser().getId(), "Upcoming shift",
						"Your booking starts at " + formattedTime(booking) + ". Tap Start trip when you leave.");
				bookingAttendanceRepository.markReminderSent(row.getId());
			} catch (RuntimeException e) {
				log.error("Could not send trip reminder for bookingAttendanceId={}", row.getId(), e);
			}
		}
	}

	private String formattedTime(Booking booking) {
		return TIME_FORMAT.format(booking.getStartTime().atZone(INDIA_ZONE));
	}

}
