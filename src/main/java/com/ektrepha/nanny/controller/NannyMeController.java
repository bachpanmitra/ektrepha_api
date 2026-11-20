package com.ektrepha.nanny.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.nanny.dto.request.NannyEmergencyContactRequest;
import com.ektrepha.nanny.dto.request.NannyMeUpdateRequest;
import com.ektrepha.nanny.dto.response.NannyEmergencyContactResponse;
import com.ektrepha.nanny.dto.response.NannyMeResponse;
import com.ektrepha.nanny.service.NannyMeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The nanny app's own Profile screen - distinct from {@link NannyProfileController}, which is the PARENT-facing public read-only view of a nanny. */
@RestController
@RequestMapping("/api/v1/nanny/me")
@RequiredArgsConstructor
public class NannyMeController {

	private final NannyMeService nannyMeService;

	@GetMapping
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<NannyMeResponse> getMe(Authentication authentication) {
		return ResponseEntity.ok(nannyMeService.getMe(userId(authentication)));
	}

	@PatchMapping
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<NannyMeResponse> updateMe(Authentication authentication, @Valid @RequestBody NannyMeUpdateRequest request) {
		return ResponseEntity.ok(nannyMeService.updateMe(userId(authentication), request));
	}

	@GetMapping("/emergency-contact")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<NannyEmergencyContactResponse> getEmergencyContact(Authentication authentication) {
		return ResponseEntity.ok(nannyMeService.getEmergencyContact(userId(authentication)));
	}

	@PutMapping("/emergency-contact")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<NannyEmergencyContactResponse> saveEmergencyContact(Authentication authentication,
			@Valid @RequestBody NannyEmergencyContactRequest request) {
		return ResponseEntity.ok(nannyMeService.saveEmergencyContact(userId(authentication), request));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
