package com.ektrepha.hourlycare.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.hourlycare.dto.request.AvailabilityCheckRequest;
import com.ektrepha.hourlycare.dto.request.HourlyCareBookingCreateRequest;
import com.ektrepha.hourlycare.dto.request.PaymentInitiateRequest;
import com.ektrepha.hourlycare.dto.response.AvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.BookingStatusResponse;
import com.ektrepha.hourlycare.dto.response.HourlyCareBookingResponse;
import com.ektrepha.hourlycare.dto.response.PaymentInitiateResponse;
import com.ektrepha.hourlycare.service.HourlyCareService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The "Hourly care" Details -> Review -> Payment -> Booking status flow: no nanny is picked by the parent, an in-house caregiver is assigned after payment (see {@link HourlyCareAdminController}). */
@RestController
@RequestMapping("/api/v1/hourly-care")
@RequiredArgsConstructor
public class HourlyCareController {

	private final HourlyCareService hourlyCareService;

	// Details screen's "Check availability" - no persistence, just a quote + capacity check.
	@PostMapping("/availability")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<AvailabilityResponse> checkAvailability(Authentication authentication, @Valid @RequestBody AvailabilityCheckRequest request) {
		return ResponseEntity.ok(hourlyCareService.checkAvailability(userId(authentication), request));
	}

	// Review screen's "Continue to payment" - reserves the slot as AWAITING_PAYMENT.
	@PostMapping("/bookings")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<HourlyCareBookingResponse> createBooking(Authentication authentication, @Valid @RequestBody HourlyCareBookingCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(hourlyCareService.createBooking(userId(authentication), request));
	}

	// Payment screen's method picker.
	@PostMapping("/bookings/{id}/payment")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<PaymentInitiateResponse> initiatePayment(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody PaymentInitiateRequest request) {
		return ResponseEntity.ok(hourlyCareService.initiatePayment(userId(authentication), id, request));
	}

	// Stands in for a payment gateway's success webhook (no gateway integrated yet).
	@PostMapping("/payments/{paymentId}/confirm")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<BookingStatusResponse> confirmPayment(Authentication authentication, @PathVariable Long paymentId) {
		return ResponseEntity.ok(hourlyCareService.confirmPayment(userId(authentication), paymentId));
	}

	// Stands in for a payment gateway's failure webhook.
	@PostMapping("/payments/{paymentId}/fail")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<BookingStatusResponse> failPayment(Authentication authentication, @PathVariable Long paymentId) {
		return ResponseEntity.ok(hourlyCareService.failPayment(userId(authentication), paymentId));
	}

	// Booking status screen - polled after payment.
	@GetMapping("/bookings/{id}/status")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<BookingStatusResponse> status(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(hourlyCareService.status(userId(authentication), id));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
