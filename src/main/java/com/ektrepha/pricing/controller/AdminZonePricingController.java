package com.ektrepha.pricing.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.pricing.dto.request.ZonePricingCreateRequest;
import com.ektrepha.pricing.dto.response.ZonePricingResponse;
import com.ektrepha.pricing.service.ZonePricingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/zones/{zoneId}/pricing")
@RequiredArgsConstructor
public class AdminZonePricingController {

	private final ZonePricingService zonePricingService;

	@GetMapping
	public ResponseEntity<List<ZonePricingResponse>> list(@PathVariable Long zoneId) {
		return ResponseEntity.ok(zonePricingService.list(zoneId));
	}

	@PostMapping
	public ResponseEntity<ZonePricingResponse> create(@PathVariable Long zoneId, @Valid @RequestBody ZonePricingCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(zonePricingService.create(zoneId, request));
	}

}
