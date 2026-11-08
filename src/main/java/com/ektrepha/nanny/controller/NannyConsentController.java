package com.ektrepha.nanny.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.model.NannyConsent;
import com.ektrepha.nanny.dto.request.ConsentGrantRequest;
import com.ektrepha.nanny.dto.response.ConsentResponse;
import com.ektrepha.nanny.service.NannyConsentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Nanny self-service consent (API Map row 39) — gates document upload and live-location collection elsewhere, see {@code NannyConsentService#isGranted}. */
@RestController
@RequestMapping("/api/v1/nanny/consents")
@RequiredArgsConstructor
public class NannyConsentController {

	private final NannyConsentService nannyConsentService;

	@PostMapping
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<ConsentResponse> grant(Authentication authentication, @Valid @RequestBody ConsentGrantRequest request) {
		NannyConsent consent = nannyConsentService.grant(userId(authentication), request.type());
		return ResponseEntity.ok(new ConsentResponse(consent.getType().name(), consent.getGrantedAt()));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
