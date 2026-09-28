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
import com.ektrepha.hourlycare.dto.request.MonthlyAvailabilityCheckRequest;
import com.ektrepha.hourlycare.dto.request.MonthlyBookingCreateRequest;
import com.ektrepha.hourlycare.dto.request.PaymentConfirmRequest;
import com.ektrepha.hourlycare.dto.request.PaymentInitiateRequest;
import com.ektrepha.hourlycare.dto.response.AvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.BookingStatusResponse;
import com.ektrepha.hourlycare.dto.response.HourlyCareBookingResponse;
import com.ektrepha.hourlycare.dto.response.MonthlyAvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.MonthlyBookingResponse;
import com.ektrepha.hourlycare.dto.response.PaymentInitiateResponse;
import com.ektrepha.hourlycare.service.HourlyCareService;
import com.ektrepha.pricing.dto.response.PriceQuoteResponse;

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

	// Price-only quote - same inputs as /availability, minus the capacity check. Lets the client show
	// a price before/without running the (heavier) availability check.
	@PostMapping("/price")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<PriceQuoteResponse> getPrice(Authentication authentication, @Valid @RequestBody AvailabilityCheckRequest request) {
		return ResponseEntity.ok(hourlyCareService.getPrice(userId(authentication), request));
	}

	// Review screen's "Continue to payment" - reserves the slot as AWAITING_PAYMENT.
	@PostMapping("/bookings")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<HourlyCareBookingResponse> createBooking(Authentication authentication, @Valid @RequestBody HourlyCareBookingCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(hourlyCareService.createBooking(userId(authentication), request));
	}

	// Monthly care Details screen's "Review care schedule" - price for every occurrence on the
	// selected weekdays between startDate and endDate, summed into one total.
	@PostMapping("/monthly/availability")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<MonthlyAvailabilityResponse> checkMonthlyAvailability(Authentication authentication, @Valid @RequestBody MonthlyAvailabilityCheckRequest request) {
		return ResponseEntity.ok(hourlyCareService.checkMonthlyAvailability(userId(authentication), request));
	}

	// Monthly care Review screen's "Continue to payment" - reserves every occurrence as AWAITING_PAYMENT.
	// Its response id is a normal booking id, paid via the same /bookings/{id}/payment below.
	@PostMapping("/monthly/bookings")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<MonthlyBookingResponse> createMonthlyBooking(Authentication authentication, @Valid @RequestBody MonthlyBookingCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(hourlyCareService.createMonthlyBooking(userId(authentication), request));
	}

	// Payment screen's method picker.
	@PostMapping("/bookings/{id}/payment")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<PaymentInitiateResponse> initiatePayment(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody PaymentInitiateRequest request) {
		return ResponseEntity.ok(hourlyCareService.initiatePayment(userId(authentication), id, request));
	}

	// Client-side confirm after Razorpay Checkout succeeds - signature-verified against the order
	// from initiatePayment. RazorpayWebhookController is the authoritative fallback if this is
	// never called (app killed after payment, network drop, etc).
	@PostMapping("/payments/{paymentId}/confirm")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<BookingStatusResponse> confirmPayment(Authentication authentication, @PathVariable Long paymentId,
			@Valid @RequestBody PaymentConfirmRequest request) {
		return ResponseEntity.ok(hourlyCareService.confirmPayment(userId(authentication), paymentId, request));
	}

	// The parent explicitly bails out of Razorpay Checkout (closed/cancelled) - no charge to verify.
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
