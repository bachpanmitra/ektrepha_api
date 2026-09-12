package com.ektrepha.pricing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.pricing.dto.request.DynamicPricingConfigUpdateRequest;
import com.ektrepha.pricing.dto.response.DemandSnapshotResponse;
import com.ektrepha.pricing.dto.response.DynamicPricingConfigResponse;
import com.ektrepha.pricing.service.DemandPricingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/zones/{zoneId}/service-types/{serviceTypeId}")
@RequiredArgsConstructor
public class AdminDynamicPricingController {

	private final DemandPricingService demandPricingService;

	@GetMapping("/dynamic-pricing")
	public ResponseEntity<DynamicPricingConfigResponse> getConfig(@PathVariable Long zoneId, @PathVariable Long serviceTypeId) {
		return ResponseEntity.ok(demandPricingService.getConfig(zoneId, serviceTypeId));
	}

	@PutMapping("/dynamic-pricing")
	public ResponseEntity<DynamicPricingConfigResponse> updateConfig(@PathVariable Long zoneId, @PathVariable Long serviceTypeId,
			@Valid @RequestBody DynamicPricingConfigUpdateRequest request) {
		return ResponseEntity.ok(demandPricingService.updateConfig(zoneId, serviceTypeId, request));
	}

	// Ops dashboard read - current live snapshot for this zone x service-type.
	@GetMapping("/demand-snapshot")
	public ResponseEntity<DemandSnapshotResponse> getSnapshot(@PathVariable Long zoneId, @PathVariable Long serviceTypeId) {
		return ResponseEntity.ok(demandPricingService.getSnapshot(zoneId, serviceTypeId));
	}

}
