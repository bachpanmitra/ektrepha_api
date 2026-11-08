package com.ektrepha.booking.impl;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.AttendanceStatus;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * Creates a booking's {@code booking_attendance} row for one date, used by both
 * {@link DailyAttendanceCodeGenerationJob} and the lazy create-if-missing fallback on the first
 * nanny/parent request of the day (OTP API Spec sheet row 65: "or create on first request of the
 * day"). On an approved leave day (OTP Rules sheet rule 5) this still creates a row — status
 * OFF_DAY, no codes — rather than leaving a silent gap, so the month view (API Spec sheet rows 2,
 * 12, 15) shows the day explicitly instead of looking like the job never ran. A day with no
 * {@code Booking} row at all (weekly off) never reaches this method in the first place.
 */
@Component
@RequiredArgsConstructor
public class AttendanceCodeGenerator {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final BookingAttendanceRepository bookingAttendanceRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final AttendanceOtpCache attendanceOtpCache;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public Optional<BookingAttendance> ensureRow(Booking booking, LocalDate date) {
		Optional<BookingAttendance> existing = bookingAttendanceRepository.findByBookingIdAndAttendanceDate(booking.getId(), date);
		if (existing.isPresent()) {
			return existing;
		}
		if (booking.getNanny() == null) {
			return Optional.empty();
		}
		if (leaveRequestRepository.existsApprovedForNannyAndDate(booking.getNanny().getId(), date)) {
			BookingAttendance offDayRow = BookingAttendance.builder()
					.booking(booking).attendanceDate(date).status(AttendanceStatus.OFF_DAY)
					.build();
			return Optional.of(bookingAttendanceRepository.save(offDayRow));
		}

		String checkInCode = generateCode();
		String checkOutCode = generateCode();
		BookingAttendance row = BookingAttendance.builder()
				.booking(booking)
				.attendanceDate(date)
				.checkInOtpHash(passwordEncoder.encode(checkInCode))
				.checkOutOtpHash(passwordEncoder.encode(checkOutCode))
				.status(AttendanceStatus.PENDING)
				.build();
		row = bookingAttendanceRepository.save(row);
		attendanceOtpCache.put(booking.getId(), date, true, checkInCode);
		attendanceOtpCache.put(booking.getId(), date, false, checkOutCode);
		return Optional.of(row);
	}

	/** Parent "I shared the code by mistake" flow (OTP Rules sheet rule 10) - replaces just one side's code; the other side's code and any existing attempts/lock state are untouched. */
	@Transactional
	public String regenerateOne(BookingAttendance row, boolean checkIn) {
		String newCode = generateCode();
		if (checkIn) {
			row.setCheckInOtpHash(passwordEncoder.encode(newCode));
			row.setCheckInAttempts((short) 0);
		} else {
			row.setCheckOutOtpHash(passwordEncoder.encode(newCode));
			row.setCheckOutAttempts((short) 0);
		}
		bookingAttendanceRepository.save(row);
		attendanceOtpCache.put(row.getBooking().getId(), row.getAttendanceDate(), checkIn, newCode);
		return newCode;
	}

	private String generateCode() {
		return String.format("%04d", RANDOM.nextInt(10_000));
	}

}
