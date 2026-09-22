package com.ektrepha.hourlycare.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.hourlycare.dto.request.AssignCaregiverRequest;
import com.ektrepha.hourlycare.dto.response.CaregiverAssignmentResponse;
import com.ektrepha.hourlycare.service.HourlyCareService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Ops-only: assigns the actual in-house caregiver once a hourly-care booking has moved to ASSIGNING_CAREGIVER. Gated by SecurityConfig's existing hasRole('ADMIN') on /api/v1/admin/**. */
@RestController
@RequestMapping("/api/v1/admin/hourly-care")
@RequiredArgsConstructor
public class HourlyCareAdminController {

	private final HourlyCareService hourlyCareService;

	@PostMapping("/bookings/{id}/assign")
	public ResponseEntity<CaregiverAssignmentResponse> assign(@PathVariable Long id, @Valid @RequestBody AssignCaregiverRequest request) {
		return ResponseEntity.ok(hourlyCareService.assignCaregiver(id, request));
	}

}
