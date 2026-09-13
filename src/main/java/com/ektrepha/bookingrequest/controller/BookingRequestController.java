package com.ektrepha.bookingrequest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.bookingrequest.dto.request.BookingRequestCreateRequest;
import com.ektrepha.bookingrequest.dto.response.BookingRequestResponse;
import com.ektrepha.bookingrequest.service.BookingRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/booking-requests")
@RequiredArgsConstructor
public class BookingRequestController {

	private final BookingRequestService bookingRequestService;

	// Public — a guest who just got a price quote (see UserController#identify) hasn't logged in,
	// so this can't require auth. Anyone submitting here is already tied to a real user row.
	@PostMapping
	public ResponseEntity<BookingRequestResponse> submit(@Valid @RequestBody BookingRequestCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(bookingRequestService.submit(request));
	}

}
