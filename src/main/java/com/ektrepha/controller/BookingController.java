package com.ektrepha.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample endpoint only, demonstrating role-gated access for the booking
 * domain. No Booking entity/service exists yet.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

	@PostMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<Map<String, String>> createBooking(Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
				"message", "Booking created",
				"requestedBy", authentication.getName()));
	}

}
