package com.ektrepha.booking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.booking.dto.response.BookingDetailResponse;
import com.ektrepha.booking.dto.response.BookingListResponse;
import com.ektrepha.booking.service.BookingReadService;

import lombok.RequiredArgsConstructor;

/**
 * Read side only (B1/B2/H1/H2) — the existing {@code com.ektrepha.controller.BookingController}
 * still owns {@code POST /api/v1/bookings} as a placeholder; booking creation/cancel is out of
 * scope here, gated on the payments decision (PRD v2 §16.4/§17).
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingReadController {

	private final BookingReadService bookingReadService;

	@GetMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<BookingListResponse> list(Authentication authentication,
			@RequestParam String scope,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		Long userId = Long.valueOf(authentication.getName());
		return ResponseEntity.ok(bookingReadService.list(userId, scope, page, pageSize));
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<BookingDetailResponse> get(Authentication authentication, @PathVariable Long id) {
		Long userId = Long.valueOf(authentication.getName());
		return ResponseEntity.ok(bookingReadService.get(userId, id));
	}

}
