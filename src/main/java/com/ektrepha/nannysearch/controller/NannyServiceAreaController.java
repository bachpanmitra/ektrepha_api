package com.ektrepha.nannysearch.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.nannysearch.dto.request.NannyServiceAreaRequest;
import com.ektrepha.nannysearch.dto.response.NannyServiceAreaResponse;
import com.ektrepha.nannysearch.service.NannyServiceAreaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/nannies/me")
@RequiredArgsConstructor
public class NannyServiceAreaController {

	private final NannyServiceAreaService nannyServiceAreaService;

	// Sets (or edits) the authenticated nanny's own service area — idempotent, one row per nanny.
	@PutMapping("/service-area")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<NannyServiceAreaResponse> setServiceArea(Authentication authentication, @Valid @RequestBody NannyServiceAreaRequest request) {
		Long userId = Long.valueOf(authentication.getName());
		return ResponseEntity.ok(nannyServiceAreaService.setServiceArea(userId, request));
	}

}
