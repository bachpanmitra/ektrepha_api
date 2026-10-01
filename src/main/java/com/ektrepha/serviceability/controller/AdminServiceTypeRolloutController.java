package com.ektrepha.serviceability.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.serviceability.dto.request.ServiceTypeRolloutRequest;
import com.ektrepha.serviceability.dto.response.ServiceTypeRolloutResponse;
import com.ektrepha.serviceability.service.ServiceTypeRolloutService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/zones/{zoneId}/service-types")
@RequiredArgsConstructor
public class AdminServiceTypeRolloutController {

	private final ServiceTypeRolloutService serviceTypeRolloutService;

	// One row per service type, NOT_PLANNED-defaulted for any without a rollout row yet — backs the
	// admin Zones & Pricing screen's per-zone rollout table (no such listing existed before this).
	@GetMapping
	public ResponseEntity<List<ServiceTypeRolloutResponse>> list(@PathVariable Long zoneId) {
		return ResponseEntity.ok(serviceTypeRolloutService.listForZone(zoneId));
	}

	@PatchMapping("/{serviceTypeId}")
	public ResponseEntity<ServiceTypeRolloutResponse> setStatus(@PathVariable Long zoneId, @PathVariable Long serviceTypeId,
			@Valid @RequestBody ServiceTypeRolloutRequest request) {
		return ResponseEntity.ok(serviceTypeRolloutService.setStatus(zoneId, serviceTypeId, request));
	}

}
