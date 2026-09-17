package com.ektrepha.parent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.parent.dto.request.ParentProfileUpdateRequest;
import com.ektrepha.parent.dto.response.ParentProfileResponse;
import com.ektrepha.parent.service.ParentProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/parents/me")
@RequiredArgsConstructor
public class ParentProfileController {

	private final ParentProfileService parentProfileService;

	@GetMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ParentProfileResponse> get(Authentication authentication) {
		Long userId = Long.valueOf(authentication.getName());
		return ResponseEntity.ok(parentProfileService.get(userId));
	}

	@PutMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ParentProfileResponse> upsert(Authentication authentication, @Valid @RequestBody ParentProfileUpdateRequest request) {
		Long userId = Long.valueOf(authentication.getName());
		return ResponseEntity.ok(parentProfileService.upsert(userId, request));
	}

}
