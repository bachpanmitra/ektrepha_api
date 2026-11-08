package com.ektrepha.booking.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.repository.BookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates today's check-in/check-out codes for every active booking (nanny app spec: "Create
 * today's codes for every active booking", scheduler at 00:05 IST). Same per-item try/catch shape
 * as {@link com.ektrepha.booking.impl.BookingStaleAutoCancelJob} - one booking's failure doesn't
 * stop the sweep. {@link AttendanceCodeGenerator#ensureRow} is also called lazily on the first
 * check-in/attendance-today request of the day, so a missed or delayed run here isn't fatal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyAttendanceCodeGenerationJob {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final Set<BookingStatus> ACTIVE_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

	private final BookingRepository bookingRepository;
	private final AttendanceCodeGenerator attendanceCodeGenerator;

	@Scheduled(cron = "${app.booking.attendance-code-cron:0 5 0 * * *}", zone = "Asia/Kolkata")
	public void generateTodayCodes() {
		LocalDate today = LocalDate.now(INDIA_ZONE);
		Instant dayStart = today.atStartOfDay(INDIA_ZONE).toInstant();
		Instant dayEnd = today.plusDays(1).atStartOfDay(INDIA_ZONE).toInstant();

		List<Booking> todaysBookings = bookingRepository.findByStatusInAndStartTimeBetween(ACTIVE_STATUSES, dayStart, dayEnd);
		int generated = 0;
		for (Booking booking : todaysBookings) {
			try {
				if (attendanceCodeGenerator.ensureRow(booking, today).isPresent()) {
					generated++;
				}
			} catch (RuntimeException e) {
				log.error("Could not generate attendance codes for bookingId={}", booking.getId(), e);
			}
		}
		log.info("Daily attendance code generation: {} booking(s) found, {} row(s) created/already present", todaysBookings.size(), generated);
	}

}
