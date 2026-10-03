package com.ektrepha.verification.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.model.NannyReference;
import com.ektrepha.verification.dto.request.NannyReferenceSubmitRequest;
import com.ektrepha.verification.dto.request.VerificationRejectRequest;
import com.ektrepha.verification.dto.response.NannyReferenceResponse;
import com.ektrepha.verification.service.NannyReferenceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** PRD: "Minimum 2 references, with verification status recorded by admin." */
@Tag(name = "Nanny References", description = "Nanny reference submission and admin verification.")
@RestController
@RequestMapping("/api/v1/nanny-verification/references")
@RequiredArgsConstructor
public class NannyReferenceController {

	private final NannyReferenceService nannyReferenceService;

	@Operation(summary = "Submit a reference", description = "Adds a new reference for the caller's own nanny profile as PENDING.")
	@PostMapping
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<NannyReferenceResponse> submit(Authentication authentication, @Valid @RequestBody NannyReferenceSubmitRequest request) {
		NannyReference reference = nannyReferenceService.submit(userId(authentication), request.name(), request.phone(), request.relationship());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reference));
	}

	@Operation(summary = "List a nanny's references", description = "Admin-only lookup by nannyId.")
	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<NannyReferenceResponse>> list(@RequestParam Long nannyId) {
		return ResponseEntity.ok(nannyReferenceService.list(nannyId).stream().map(this::toResponse).toList());
	}

	@Operation(summary = "Verify a reference", description = "Marks one reference VERIFIED and recomputes the owning nanny's rollup.")
	@PostMapping("/{id}/verify")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<NannyReferenceResponse> verify(Authentication authentication, @Parameter(description = "nanny_reference row id") @PathVariable Long id) {
		return ResponseEntity.ok(toResponse(nannyReferenceService.verify(id, userId(authentication))));
	}

	@Operation(summary = "Reject a reference", description = "Marks one reference REJECTED with a reason and recomputes the owning nanny's rollup.")
	@PostMapping("/{id}/reject")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<NannyReferenceResponse> reject(Authentication authentication, @PathVariable Long id, @Valid @RequestBody VerificationRejectRequest request) {
		return ResponseEntity.ok(toResponse(nannyReferenceService.reject(id, userId(authentication), request.reason())));
	}

	private NannyReferenceResponse toResponse(NannyReference reference) {
		return new NannyReferenceResponse(reference.getId(), reference.getNanny().getId(), reference.getName(), reference.getPhone(),
				reference.getRelationship(), reference.getStatus().name(), reference.getCreatedAt(), reference.getVerifiedAt(), reference.getRejectionReason());
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
