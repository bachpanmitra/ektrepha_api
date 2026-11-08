package com.ektrepha.workforce.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.activity.service.OrderActivityService;
import com.ektrepha.booking.dto.response.AttendanceMonthResponse;
import com.ektrepha.booking.impl.AttendanceCodeGenerator;
import com.ektrepha.booking.impl.AttendanceMonthMapper;
import com.ektrepha.booking.impl.GeoUtil;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.InvalidBookingTransitionException;
import com.ektrepha.exception.LeaveDayException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.OffDayException;
import com.ektrepha.exception.OtpLockedException;
import com.ektrepha.exception.TooFarException;
import com.ektrepha.exception.WrongOtpException;
import com.ektrepha.model.AttendanceMethod;
import com.ektrepha.model.AttendanceStatus;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.OrderActivityType;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.workforce.dto.response.AttendanceResponse;
import com.ektrepha.workforce.service.NannyAttendanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NannyAttendanceServiceImpl implements NannyAttendanceService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final int MAX_ATTEMPTS = 5;
	private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
	private static final double MAX_DISTANCE_M = 200;

	private final NannyRepository nannyRepository;
	private final BookingRepository bookingRepository;
	private final BookingAttendanceRepository bookingAttendanceRepository;
	private final AttendanceCodeGenerator attendanceCodeGenerator;
	private final AttendanceMonthMapper attendanceMonthMapper;
	private final PasswordEncoder passwordEncoder;
	private final PushSenderService pushSenderService;
	private final OrderActivityService orderActivityService;

	@Override
	@Transactional
	public AttendanceResponse checkIn(Long callerUserId, Long bookingId, String otp, double lat, double lng) {
		Booking booking = resolveOwnBooking(callerUserId, bookingId);
		LocalDate today = LocalDate.now(INDIA_ZONE);
		BookingAttendance row = resolveTodayRow(booking, today);

		if (row.getStatus() == AttendanceStatus.CHECKED_IN || row.getStatus() == AttendanceStatus.CHECKED_OUT) {
			throw new InvalidBookingTransitionException("This booking has already been checked into today");
		}
		rejectIfLocked(row);
		rejectIfTooFar(booking, lat, lng);
		verifyOtpOrThrow(row, row.getCheckInOtpHash(), otp, true);

		row.setCheckedInAt(Instant.now());
		row.setCheckInMethod(AttendanceMethod.OTP);
		row.setCheckInLat(lat);
		row.setCheckInLng(lng);
		row.setStatus(AttendanceStatus.CHECKED_IN);
		row = bookingAttendanceRepository.save(row);

		orderActivityService.log(booking, OrderActivityType.CHECKED_IN, OrderActivityActorType.NANNY, booking.getNanny().getId(), nannyFirstName(booking));
		log.info("Nanny checked in via OTP: bookingId={}, nannyId={}, date={}", booking.getId(), booking.getNanny().getId(), today);
		notifyParent(booking, "Your nanny has arrived", nannyFirstName(booking) + " just checked in for booking BK-" + booking.getId() + ".");
		return toResponse(row);
	}

	@Override
	@Transactional
	public AttendanceResponse checkOut(Long callerUserId, Long bookingId, String otp, double lat, double lng) {
		Booking booking = resolveOwnBooking(callerUserId, bookingId);
		BookingAttendance row = resolveOpenCheckInRow(booking);

		rejectIfLocked(row);
		rejectIfTooFar(booking, lat, lng);
		verifyOtpOrThrow(row, row.getCheckOutOtpHash(), otp, false);

		row.setCheckedOutAt(Instant.now());
		row.setCheckOutMethod(AttendanceMethod.OTP);
		row.setCheckOutLat(lat);
		row.setCheckOutLng(lng);
		row.setStatus(AttendanceStatus.CHECKED_OUT);
		row = bookingAttendanceRepository.save(row);

		orderActivityService.log(booking, OrderActivityType.CHECKED_OUT, OrderActivityActorType.NANNY, booking.getNanny().getId(), nannyFirstName(booking));
		log.info("Nanny checked out via OTP: bookingId={}, nannyId={}, date={}", booking.getId(), booking.getNanny().getId(), row.getAttendanceDate());
		notifyParent(booking, "Your nanny has left", nannyFirstName(booking) + " just checked out of booking BK-" + booking.getId() + ".");
		return toResponse(row);
	}

	@Override
	@Transactional(readOnly = true)
	public AttendanceMonthResponse month(Long callerUserId, Long bookingId, String month) {
		Booking booking = resolveOwnBooking(callerUserId, bookingId);
		LocalDate from = LocalDate.parse(month + "-01");
		LocalDate to = from.plusMonths(1).minusDays(1);
		Long groupId = booking.getRecurrenceGroupId() != null ? booking.getRecurrenceGroupId() : booking.getId();
		var rows = bookingAttendanceRepository.findForSeriesInMonth(groupId, from, to);
		return attendanceMonthMapper.toResponse(bookingId, month, rows, false);
	}

	/** Today's row, created on the spot if the job hasn't run yet (OTP API Spec sheet row 65). */
	private BookingAttendance resolveTodayRow(Booking booking, LocalDate today) {
		Optional<BookingAttendance> row = attendanceCodeGenerator.ensureRow(booking, today);
		if (row.isEmpty()) {
			throw new OffDayException("No shift scheduled for today");
		}
		if (row.get().getStatus() == AttendanceStatus.OFF_DAY) {
			throw new LeaveDayException("Nanny is on approved leave today");
		}
		return row.get();
	}

	/**
	 * Resolves the open (checked-in, not yet checked-out) row for a check-out call — today's row
	 * first, then yesterday's, so a shift that started before midnight and ends after it (NAO-60)
	 * still uses the start date's own check-out code rather than failing to find "today"'s row.
	 */
	private BookingAttendance resolveOpenCheckInRow(Booking booking) {
		LocalDate today = LocalDate.now(INDIA_ZONE);
		for (LocalDate candidate : new LocalDate[] { today, today.minusDays(1) }) {
			Optional<BookingAttendance> row = bookingAttendanceRepository.findByBookingIdAndAttendanceDate(booking.getId(), candidate);
			if (row.isPresent() && row.get().getStatus() == AttendanceStatus.CHECKED_IN) {
				return row.get();
			}
		}
		throw new InvalidBookingTransitionException("This booking hasn't been checked into yet");
	}

	private void rejectIfLocked(BookingAttendance row) {
		if (row.getLockedUntil() != null && row.getLockedUntil().isAfter(Instant.now())) {
			throw new OtpLockedException(row.getLockedUntil());
		}
	}

	// Location is checked before the code itself (OTP Rules sheet rule 13) and a bad location never
	// counts as a wrong-code attempt (NAO-56) - a code can be guessed from far away just as easily
	// as nearby, so only an actual wrong code should burn one of the 5 tries.
	private void rejectIfTooFar(Booking booking, double lat, double lng) {
		if (booking.getAddress() == null || booking.getAddress().getLat() == null || booking.getAddress().getLng() == null) {
			return;
		}
		double distanceM = GeoUtil.distanceMeters(lat, lng, booking.getAddress().getLat(), booking.getAddress().getLng());
		if (distanceM > MAX_DISTANCE_M) {
			throw new TooFarException(distanceM);
		}
	}

	private void verifyOtpOrThrow(BookingAttendance row, String hash, String otp, boolean isCheckIn) {
		if (passwordEncoder.matches(otp, hash)) {
			return;
		}
		short attempts = (short) ((isCheckIn ? row.getCheckInAttempts() : row.getCheckOutAttempts()) + 1);
		if (isCheckIn) {
			row.setCheckInAttempts(attempts);
		} else {
			row.setCheckOutAttempts(attempts);
		}
		if (attempts >= MAX_ATTEMPTS) {
			Instant lockedUntil = Instant.now().plus(LOCK_DURATION);
			row.setLockedUntil(lockedUntil);
			bookingAttendanceRepository.save(row);
			// No dedicated ops-alert channel exists yet (PushSenderService only sends to one known
			// user) - logged at ERROR so it surfaces in whatever log monitoring ops already has,
			// same stand-in this codebase uses elsewhere until a real ops-broadcast exists.
			log.error("Attendance OTP locked: bookingAttendanceId={}, bookingId={}, isCheckIn={}", row.getId(), row.getBooking().getId(), isCheckIn);
			throw new OtpLockedException(lockedUntil);
		}
		bookingAttendanceRepository.save(row);
		throw new WrongOtpException(MAX_ATTEMPTS - attempts);
	}

	private void notifyParent(Booking booking, String title, String body) {
		pushSenderService.sendToUser(booking.getParent().getUser().getId(), title, body);
	}

	private String nannyFirstName(Booking booking) {
		return booking.getNanny().getFirstName();
	}

	private Booking resolveOwnBooking(Long callerUserId, Long bookingId) {
		Nanny nanny = nannyRepository.findByUserId(callerUserId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile found for this account"));
		return bookingRepository.findByIdAndNannyId(bookingId, nanny.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking with id " + bookingId + " assigned to this nanny"));
	}

	private AttendanceResponse toResponse(BookingAttendance row) {
		Long minutes = (row.getCheckedInAt() != null && row.getCheckedOutAt() != null)
				? Duration.between(row.getCheckedInAt(), row.getCheckedOutAt()).toMinutes()
				: null;
		return new AttendanceResponse(row.getBooking().getId(), row.getAttendanceDate(), row.getStatus().name(),
				row.getCheckedInAt(), row.getCheckedOutAt(),
				row.getCheckInMethod() == null ? null : row.getCheckInMethod().name(),
				row.getCheckOutMethod() == null ? null : row.getCheckOutMethod().name(), minutes);
	}

}
