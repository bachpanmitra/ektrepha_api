package com.ektrepha.booking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.booking.dto.request.RegenerateOtpRequest;
import com.ektrepha.booking.dto.response.AttendanceMonthResponse;
import com.ektrepha.booking.dto.response.RegenerateOtpResponse;
import com.ektrepha.booking.dto.response.TodayAttendanceResponse;
import com.ektrepha.booking.service.BookingAttendanceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** PARENT-side reads/actions on their own booking's daily attendance codes (API Spec sheet rows 13-15). */
@RestController
@RequestMapping("/api/v1/bookings/{id}/attendance")
@RequiredArgsConstructor
public class BookingAttendanceController {

	private final BookingAttendanceService bookingAttendanceService;

	@GetMapping("/today")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<TodayAttendanceResponse> today(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(bookingAttendanceService.today(userId(authentication), id));
	}

	@PostMapping("/today/regenerate-otp")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<RegenerateOtpResponse> regenerateOtp(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody RegenerateOtpRequest request) {
		return ResponseEntity.ok(bookingAttendanceService.regenerateOtp(userId(authentication), id, request.type()));
	}

	@GetMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<AttendanceMonthResponse> month(Authentication authentication, @PathVariable Long id, @RequestParam String month) {
		return ResponseEntity.ok(bookingAttendanceService.month(userId(authentication), id, month));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
