package com.ektrepha.pricing.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.pricing.dto.request.ZonePricingRuleCreateRequest;
import com.ektrepha.pricing.dto.request.ZonePricingRuleUpdateRequest;
import com.ektrepha.pricing.dto.request.ZonePricingUpdateRequest;
import com.ektrepha.pricing.dto.response.ZonePricingResponse;
import com.ektrepha.pricing.dto.response.ZonePricingRuleResponse;
import com.ektrepha.pricing.service.ZonePricingRuleService;
import com.ektrepha.pricing.service.ZonePricingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Not in the original design doc's endpoint list: surge/day-type rules (zone_pricing_rules) had no admin CRUD at all, which would leave weekend/holiday pricing unconfigurable via API — added here as the natural extension of the base pricing CRUD it's nested under. */
@RestController
@RequestMapping("/api/v1/admin/pricing")
@RequiredArgsConstructor
public class AdminPricingController {

	private final ZonePricingService zonePricingService;
	private final ZonePricingRuleService zonePricingRuleService;

	@PutMapping("/{id}")
	public ResponseEntity<ZonePricingResponse> update(@PathVariable Long id, @Valid @RequestBody ZonePricingUpdateRequest request) {
		return ResponseEntity.ok(zonePricingService.update(id, request));
	}

	@GetMapping("/{pricingId}/rules")
	public ResponseEntity<List<ZonePricingRuleResponse>> listRules(@PathVariable Long pricingId) {
		return ResponseEntity.ok(zonePricingRuleService.list(pricingId));
	}

	@PostMapping("/{pricingId}/rules")
	public ResponseEntity<ZonePricingRuleResponse> createRule(@PathVariable Long pricingId, @Valid @RequestBody ZonePricingRuleCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(zonePricingRuleService.create(pricingId, request));
	}

	@PutMapping("/rules/{id}")
	public ResponseEntity<ZonePricingRuleResponse> updateRule(@PathVariable Long id, @Valid @RequestBody ZonePricingRuleUpdateRequest request) {
		return ResponseEntity.ok(zonePricingRuleService.update(id, request));
	}

}
