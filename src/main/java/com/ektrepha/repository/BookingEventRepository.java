package com.ektrepha.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.BookingEvent;
import com.ektrepha.model.BookingEventType;

public interface BookingEventRepository extends JpaRepository<BookingEvent, Long> {

	Optional<BookingEvent> findByBookingIdAndEventDateAndType(Long bookingId, LocalDate eventDate, BookingEventType type);

	boolean existsByBookingIdAndEventDateAndType(Long bookingId, LocalDate eventDate, BookingEventType type);

}
