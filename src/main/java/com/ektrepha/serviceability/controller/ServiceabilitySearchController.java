package com.ektrepha.serviceability.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.serviceability.dto.response.ServiceabilityMatrixResponse;
import com.ektrepha.serviceability.service.ServiceabilitySearchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/serviceability")
@RequiredArgsConstructor
public class ServiceabilitySearchController {

	private final ServiceabilitySearchService serviceabilitySearchService;

	// Public, pre-signup discovery endpoint — a visitor checks "is this available near me" before
	// creating an account. Exactly one of pincode / query / (lat & lng) / (city & state) is expected.
	@GetMapping("/search")
	public ResponseEntity<ServiceabilityMatrixResponse> search(
			@RequestParam(required = false) String pincode,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng,
			@RequestParam(required = false) String city,
			@RequestParam(required = false) String state) {
		return ResponseEntity.ok(serviceabilitySearchService.search(pincode, query, lat, lng, city, state));
	}

}
