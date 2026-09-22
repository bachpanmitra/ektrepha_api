package com.ektrepha.booking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;
import com.ektrepha.booking.service.NannyBookingService;

import lombok.RequiredArgsConstructor;

/** The assigned caregiver's own "Live care" actions - Start/Complete - on a booking they're assigned to (any flow). Distinct base path from /api/v1/bookings, which is entirely PARENT-scoped. */
@RestController
@RequestMapping("/api/v1/nanny-bookings")
@RequiredArgsConstructor
public class NannyBookingController {

	private final NannyBookingService nannyBookingService;

	@PostMapping("/{id}/start")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<BookingLifecycleResponse> start(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(nannyBookingService.startCare(userId(authentication), id));
	}

	@PostMapping("/{id}/complete")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<BookingLifecycleResponse> complete(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(nannyBookingService.completeCare(userId(authentication), id));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
