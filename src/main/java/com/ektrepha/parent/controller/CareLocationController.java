package com.ektrepha.parent.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.parent.dto.response.CareLocationResponse;
import com.ektrepha.parent.service.CareLocationService;

import lombok.RequiredArgsConstructor;

/** Post-login care-location resolve/select - see the class-level comment on {@link com.ektrepha.parent.impl.CareLocationServiceImpl}. Falls under SecurityConfig's existing /api/v1/parents/me/** -&gt; hasRole('PARENT') matcher. */
@RestController
@RequestMapping("/api/v1/parents/me/care-location")
@RequiredArgsConstructor
public class CareLocationController {

	private final CareLocationService careLocationService;

	@GetMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<CareLocationResponse> resolve(Authentication authentication) {
		return ResponseEntity.ok(careLocationService.resolve(userId(authentication)));
	}

	@PutMapping("/{addressId}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<AddressResponse> select(Authentication authentication, @PathVariable Long addressId) {
		return ResponseEntity.ok(careLocationService.select(userId(authentication), addressId));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
