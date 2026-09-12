package com.ektrepha.pricing.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.pricing.dto.request.PriceCalculationRequest;
import com.ektrepha.pricing.dto.response.PriceQuoteResponse;
import com.ektrepha.pricing.service.PricingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

	private final PricingService pricingService;

	// Public — a visitor needs a price quote before signing up or picking a caregiver.
	@PostMapping("/calculate")
	public ResponseEntity<PriceQuoteResponse> calculate(@Valid @RequestBody PriceCalculationRequest request) {
		return ResponseEntity.ok(pricingService.calculate(request));
	}

}
