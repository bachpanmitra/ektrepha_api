package com.ektrepha.booking.impl;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingEvent;
import com.ektrepha.model.BookingEventActor;
import com.ektrepha.model.BookingEventType;
import com.ektrepha.repository.BookingEventRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The single shared write path for a booking's "on the way" event log (API Map rows 30-31; see
 * the Build Order's shared-pieces list — later rows such as SOS/incidents are expected to reuse
 * this same {@code booking_event} table once built). One row per (booking, date, type) in
 * practice, though no DB uniqueness constraint enforces that — a duplicate TRIP_STARTED simply
 * means the nanny tapped twice, harmless to log twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventService {

	private final BookingEventRepository bookingEventRepository;
	// No ObjectMapper bean is registered app-wide (Spring Boot 4's auto-config wires the newer
	// tools.jackson.* stack) - a plain local instance is enough for this small, internal JSONB blob.
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Transactional
	public BookingEvent record(Booking booking, LocalDate date, BookingEventType type, Double lat, Double lng,
			BookingEventActor actor, Map<String, Object> meta) {
		String metaJson = null;
		if (meta != null && !meta.isEmpty()) {
			try {
				metaJson = objectMapper.writeValueAsString(meta);
			} catch (Exception e) {
				log.warn("Could not serialize booking_event meta, saving without it: {}", e.getMessage());
			}
		}
		BookingEvent event = BookingEvent.builder()
				.booking(booking).eventDate(date).type(type).lat(lat).lng(lng).actor(actor).meta(metaJson)
				.build();
		return bookingEventRepository.save(event);
	}

}
