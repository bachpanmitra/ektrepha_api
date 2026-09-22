package com.ektrepha.booking.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;
import com.ektrepha.booking.service.NannyBookingService;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.InvalidBookingTransitionException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The caregiver-side counterpart to the parent's read/write booking APIs - drives the "Live care"
 * screen's status by moving a booking CONFIRMED -&gt; IN_PROGRESS -&gt; COMPLETED. Applies to any
 * assigned booking regardless of which flow created it (nanny-first or hourly-care pay-first);
 * this transition previously had no API at all, so bookings here got stuck at CONFIRMED forever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NannyBookingServiceImpl implements NannyBookingService {

	private final NannyRepository nannyRepository;
	private final BookingRepository bookingRepository;

	@Override
	@Transactional
	public BookingLifecycleResponse startCare(Long userId, Long bookingId) {
		Booking booking = resolveOwnBooking(userId, bookingId);
		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw new InvalidBookingTransitionException("Care can only be started from a CONFIRMED booking");
		}
		booking.setStatus(BookingStatus.IN_PROGRESS);
		bookingRepository.save(booking);
		log.info("Care started: bookingId={}, nannyId={}", booking.getId(), booking.getNanny().getId());
		return new BookingLifecycleResponse(booking.getId(), booking.getStatus().name());
	}

	@Override
	@Transactional
	public BookingLifecycleResponse completeCare(Long userId, Long bookingId) {
		Booking booking = resolveOwnBooking(userId, bookingId);
		if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
			throw new InvalidBookingTransitionException("Care can only be completed from an IN_PROGRESS booking");
		}
		booking.setStatus(BookingStatus.COMPLETED);
		bookingRepository.save(booking);
		log.info("Care completed: bookingId={}, nannyId={}", booking.getId(), booking.getNanny().getId());
		return new BookingLifecycleResponse(booking.getId(), booking.getStatus().name());
	}

	private Booking resolveOwnBooking(Long userId, Long bookingId) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No nanny profile found for this account"));
		return bookingRepository.findByIdAndNannyId(bookingId, nanny.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));
	}

}
