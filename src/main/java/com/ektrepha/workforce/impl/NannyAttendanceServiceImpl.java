package com.ektrepha.workforce.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.activity.service.OrderActivityService;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.InvalidBookingTransitionException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.OrderActivityType;
import com.ektrepha.notification.service.PushSenderService;
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

	private final NannyRepository nannyRepository;
	private final BookingRepository bookingRepository;
	private final PushSenderService pushSenderService;
	private final OrderActivityService orderActivityService;

	@Override
	@Transactional
	public AttendanceResponse checkIn(Long callerUserId, Long bookingId) {
		Booking booking = resolveOwnBooking(callerUserId, bookingId);
		if (booking.getCheckedInAt() != null) {
			throw new InvalidBookingTransitionException("This booking has already been checked into");
		}
		booking.setCheckedInAt(Instant.now());
		booking = bookingRepository.save(booking);
		orderActivityService.log(booking, OrderActivityType.CHECKED_IN, OrderActivityActorType.NANNY, booking.getNanny().getId(), nannyFirstName(booking));
		log.info("Nanny checked in: bookingId={}, nannyId={}", booking.getId(), booking.getNanny().getId());
		notifyParent(booking, "Your nanny has arrived",
				nannyFirstName(booking) + " just checked in for booking BK-" + booking.getId() + ".");
		return toResponse(booking);
	}

	@Override
	@Transactional
	public AttendanceResponse checkOut(Long callerUserId, Long bookingId) {
		Booking booking = resolveOwnBooking(callerUserId, bookingId);
		if (booking.getCheckedInAt() == null) {
			throw new InvalidBookingTransitionException("This booking hasn't been checked into yet");
		}
		if (booking.getCheckedOutAt() != null) {
			throw new InvalidBookingTransitionException("This booking has already been checked out of");
		}
		booking.setCheckedOutAt(Instant.now());
		booking = bookingRepository.save(booking);
		orderActivityService.log(booking, OrderActivityType.CHECKED_OUT, OrderActivityActorType.NANNY, booking.getNanny().getId(), nannyFirstName(booking));
		log.info("Nanny checked out: bookingId={}, nannyId={}", booking.getId(), booking.getNanny().getId());
		notifyParent(booking, "Your nanny has left",
				nannyFirstName(booking) + " just checked out of booking BK-" + booking.getId() + ".");
		return toResponse(booking);
	}

	// CARE_START_END is a transactional-safety notification category (never opt-out-able —
	// see NotificationServiceImpl.NON_EDITABLE), so this always fires with no preference check.
	// sendToUser itself never throws, so a push failure can't roll back the check-in/out it reports.
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

	private AttendanceResponse toResponse(Booking booking) {
		return new AttendanceResponse(booking.getId(), booking.getCheckedInAt(), booking.getCheckedOutAt());
	}

}
