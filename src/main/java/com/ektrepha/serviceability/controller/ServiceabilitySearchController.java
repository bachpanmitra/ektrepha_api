package com.ektrepha.serviceability.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.serviceability.dto.response.LiveZoneResponse;
import com.ektrepha.serviceability.dto.response.LocalityOptionResponse;
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

	// Public, pre-signup marketing endpoint — "we're live in these areas" on the homepage.
	@GetMapping("/live-zones")
	public ResponseEntity<List<LiveZoneResponse>> liveZones() {
		return ResponseEntity.ok(serviceabilitySearchService.listLiveZones());
	}

	// Public "select your area" typeahead, matched to how quick-commerce apps (Zepto, Blinkit,
	// Instamart) let a user type a locality name and pick from matches before ever seeing a price -
	// a lighter-weight sibling to /search for the same discovery moment.
	@GetMapping("/localities")
	public ResponseEntity<List<LocalityOptionResponse>> searchLocalities(
			@RequestParam String query,
			@RequestParam(required = false, defaultValue = "10") Integer limit) {
		return ResponseEntity.ok(serviceabilitySearchService.searchLocalities(query, limit));
	}

}
