package com.ektrepha.booking.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.dto.response.AttendanceMonthResponse;
import com.ektrepha.booking.dto.response.RegenerateOtpResponse;
import com.ektrepha.booking.dto.response.TodayAttendanceResponse;
import com.ektrepha.booking.service.BookingAttendanceService;
import com.ektrepha.config.RateLimiterService;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.InvalidBookingTransitionException;
import com.ektrepha.exception.OffDayException;
import com.ektrepha.exception.OtpRequestThrottledException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.AttendanceOtpType;
import com.ektrepha.model.AttendanceStatus;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

/** PARENT-side reads/actions on their own booking's daily attendance codes (API Spec sheet rows 13-15). */
@Service
@RequiredArgsConstructor
public class BookingAttendanceServiceImpl implements BookingAttendanceService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final int MAX_REGENERATIONS_PER_DAY = 3;

	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final BookingAttendanceRepository bookingAttendanceRepository;
	private final AttendanceCodeGenerator attendanceCodeGenerator;
	private final AttendanceOtpCache attendanceOtpCache;
	private final AttendanceMonthMapper attendanceMonthMapper;
	private final RateLimiterService rateLimiterService;

	@Override
	@Transactional
	public TodayAttendanceResponse today(Long parentUserId, Long bookingId) {
		Booking booking = resolveOwnBooking(parentUserId, bookingId);
		LocalDate today = LocalDate.now(INDIA_ZONE);
		Optional<BookingAttendance> rowOpt = attendanceCodeGenerator.ensureRow(booking, today);
		if (rowOpt.isEmpty()) {
			throw new OffDayException("No shift scheduled for today");
		}
		BookingAttendance row = rowOpt.get();

		String checkInOtp = row.getStatus() == AttendanceStatus.PENDING ? attendanceOtpCache.get(bookingId, today, true) : null;
		String checkOutOtp = row.getStatus() == AttendanceStatus.CHECKED_IN ? attendanceOtpCache.get(bookingId, today, false) : null;
		return new TodayAttendanceResponse(today, row.getStatus().name(), checkInOtp, checkOutOtp, row.getCheckedInAt(), row.getCheckedOutAt());
	}

	@Override
	@Transactional
	public RegenerateOtpResponse regenerateOtp(Long parentUserId, Long bookingId, AttendanceOtpType type) {
		Booking booking = resolveOwnBooking(parentUserId, bookingId);
		LocalDate today = LocalDate.now(INDIA_ZONE);
		BookingAttendance row = bookingAttendanceRepository.findByBookingIdAndAttendanceDate(bookingId, today)
				.orElseThrow(() -> new OffDayException("No shift scheduled for today"));

		boolean checkIn = type == AttendanceOtpType.CHECK_IN;
		if (checkIn && row.getStatus() != AttendanceStatus.PENDING) {
			throw new InvalidBookingTransitionException("Already used (checked in)");
		}
		if (!checkIn && row.getStatus() != AttendanceStatus.CHECKED_IN) {
			throw new InvalidBookingTransitionException("Check-out code isn't available yet");
		}
		if (!rateLimiterService.tryConsume("attendance-otp-regen:" + bookingId + ":" + today, MAX_REGENERATIONS_PER_DAY, 86_400)) {
			throw new OtpRequestThrottledException("Too many code regenerations today. Please try again tomorrow.");
		}

		String newCode = attendanceCodeGenerator.regenerateOne(row, checkIn);
		return new RegenerateOtpResponse(type.name(), newCode);
	}

	@Override
	@Transactional(readOnly = true)
	public AttendanceMonthResponse month(Long parentUserId, Long bookingId, String month) {
		Booking booking = resolveOwnBooking(parentUserId, bookingId);
		LocalDate from = LocalDate.parse(month + "-01");
		LocalDate to = from.plusMonths(1).minusDays(1);
		Long groupId = booking.getRecurrenceGroupId() != null ? booking.getRecurrenceGroupId() : booking.getId();
		var rows = bookingAttendanceRepository.findForSeriesInMonth(groupId, from, to);
		return attendanceMonthMapper.toResponse(bookingId, month, rows, false);
	}

	private Booking resolveOwnBooking(Long parentUserId, Long bookingId) {
		Parent parent = parentRepository.findByUserId(parentUserId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
		return bookingRepository.findByIdAndParentId(bookingId, parent.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking with id " + bookingId));
	}

}
