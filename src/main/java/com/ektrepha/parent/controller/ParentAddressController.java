package com.ektrepha.parent.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.parent.dto.request.AddressUpsertRequest;
import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.parent.service.ParentAddressService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/parents/me/addresses")
@RequiredArgsConstructor
public class ParentAddressController {

	private final ParentAddressService parentAddressService;

	@GetMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<List<AddressResponse>> list(Authentication authentication) {
		return ResponseEntity.ok(parentAddressService.list(userId(authentication)));
	}

	@PostMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<AddressResponse> create(Authentication authentication, @Valid @RequestBody AddressUpsertRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(parentAddressService.create(userId(authentication), request));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<AddressResponse> update(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody AddressUpsertRequest request) {
		return ResponseEntity.ok(parentAddressService.update(userId(authentication), id, request));
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
		parentAddressService.delete(userId(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}/primary")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<AddressResponse> makePrimary(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(parentAddressService.makePrimary(userId(authentication), id));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
