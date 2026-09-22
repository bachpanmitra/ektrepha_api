package com.ektrepha.booking.controller;

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

import com.ektrepha.booking.dto.request.BookingCancelRequest;
import com.ektrepha.booking.dto.request.BookingCreateRequest;
import com.ektrepha.booking.dto.response.BookingDetailResponse;
import com.ektrepha.booking.dto.response.ContactResponse;
import com.ektrepha.booking.dto.response.RebookContextResponse;
import com.ektrepha.booking.service.BookingWriteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** B4 (cancel), B5 (contact), H4 (rebook), and real booking creation — replaces the old placeholder stub. Read side (B1/B2/H1/H2) stays in {@link BookingReadController}. */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingWriteController {

	private final BookingWriteService bookingWriteService;

	@PostMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<BookingDetailResponse> create(Authentication authentication, @Valid @RequestBody BookingCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(bookingWriteService.create(userId(authentication), request));
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<BookingDetailResponse> cancel(Authentication authentication, @PathVariable Long id,
			@RequestBody(required = false) BookingCancelRequest request) {
		return ResponseEntity.ok(bookingWriteService.cancel(userId(authentication), id, request));
	}

	@GetMapping("/{id}/contact")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ContactResponse> contact(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(bookingWriteService.contact(userId(authentication), id));
	}

	@GetMapping("/{id}/rebook-context")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<RebookContextResponse> rebookContext(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(bookingWriteService.rebookContext(userId(authentication), id));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
