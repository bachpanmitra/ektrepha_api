package com.ektrepha.hourlycare.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.hourlycare.dto.request.AssignCaregiverRequest;
import com.ektrepha.hourlycare.dto.request.ReassignCaregiverRequest;
import com.ektrepha.hourlycare.dto.response.CaregiverAssignmentResponse;
import com.ektrepha.hourlycare.service.HourlyCareService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Ops-only: assigns/reassigns the actual in-house caregiver on a hourly-care booking. Gated by SecurityConfig's existing hasRole('ADMIN') on /api/v1/admin/**. */
@RestController
@RequestMapping("/api/v1/admin/hourly-care")
@RequiredArgsConstructor
public class HourlyCareAdminController {

	private final HourlyCareService hourlyCareService;

	@PostMapping("/bookings/{id}/assign")
	public ResponseEntity<CaregiverAssignmentResponse> assign(Authentication authentication, @PathVariable Long id, @Valid @RequestBody AssignCaregiverRequest request) {
		return ResponseEntity.ok(hourlyCareService.assignCaregiver(Long.valueOf(authentication.getName()), id, request));
	}

	// Emergency replacement - swaps the caregiver on a CONFIRMED, not-yet-checked-in booking (e.g. a
	// no-show). Distinct from assign above, which only works pre-CONFIRMED.
	@PostMapping("/bookings/{id}/reassign")
	public ResponseEntity<CaregiverAssignmentResponse> reassign(Authentication authentication, @PathVariable Long id, @Valid @RequestBody ReassignCaregiverRequest request) {
		return ResponseEntity.ok(hourlyCareService.reassignCaregiver(Long.valueOf(authentication.getName()), id, request));
	}

}
