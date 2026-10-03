package com.ektrepha.admin.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.admin.dto.response.AdminBookingCandidateResponse;
import com.ektrepha.admin.dto.response.AdminBookingDetailResponse;
import com.ektrepha.admin.dto.response.AdminBookingListResponse;
import com.ektrepha.admin.dto.response.AdminOrderActivityResponse;
import com.ektrepha.admin.service.AdminBookingService;
import com.ektrepha.model.BookingStatus;

import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize.
@RestController
@RequestMapping("/api/v1/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

	private final AdminBookingService adminBookingService;

	@GetMapping
	public ResponseEntity<AdminBookingListResponse> list(
			@RequestParam(required = false) BookingStatus status,
			@RequestParam(required = false) String from,
			@RequestParam(required = false) String to,
			@RequestParam(required = false) Long zoneId,
			@RequestParam(required = false) Long serviceTypeId,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminBookingService.list(status, from, to, zoneId, serviceTypeId, q, page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AdminBookingDetailResponse> detail(@PathVariable Long id) {
		return ResponseEntity.ok(adminBookingService.detail(id));
	}

	@GetMapping("/{id}/candidates")
	public ResponseEntity<List<AdminBookingCandidateResponse>> candidates(@PathVariable Long id) {
		return ResponseEntity.ok(adminBookingService.candidates(id));
	}

	@GetMapping("/{id}/activity")
	public ResponseEntity<List<AdminOrderActivityResponse>> activity(@PathVariable Long id) {
		return ResponseEntity.ok(adminBookingService.activity(id));
	}

}
