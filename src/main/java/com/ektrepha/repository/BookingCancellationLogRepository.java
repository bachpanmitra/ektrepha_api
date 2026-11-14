package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.BookingCancellationLog;

public interface BookingCancellationLogRepository extends JpaRepository<BookingCancellationLog, Long> {

	/** Care Manager feature — detecting NO_SHOW (reason_code NANNY_NO_SHOW) for the contract's Booking.status mapping. */
	Optional<BookingCancellationLog> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);

}
