package com.ektrepha.pricing.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.pricing.dto.request.ZoneServicePricingBulkUpsertRequest;
import com.ektrepha.pricing.dto.response.ZoneServicePricingRowResponse;
import com.ektrepha.pricing.service.ZonePricingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Admin bulk rate-entry screen: every area's rate for one service type, read and saved in one call instead of one zone at a time. */
@RestController
@RequestMapping("/api/v1/admin/service-types/{serviceTypeId}/zone-pricing")
@RequiredArgsConstructor
public class AdminServiceTypeZonePricingController {

	private final ZonePricingService zonePricingService;

	@GetMapping
	public ResponseEntity<List<ZoneServicePricingRowResponse>> list(@PathVariable Long serviceTypeId) {
		return ResponseEntity.ok(zonePricingService.listByServiceType(serviceTypeId));
	}

	@PutMapping
	public ResponseEntity<List<ZoneServicePricingRowResponse>> bulkUpsert(
			@PathVariable Long serviceTypeId, @Valid @RequestBody ZoneServicePricingBulkUpsertRequest request) {
		return ResponseEntity.ok(zonePricingService.bulkUpsert(serviceTypeId, request.items()));
	}

}
