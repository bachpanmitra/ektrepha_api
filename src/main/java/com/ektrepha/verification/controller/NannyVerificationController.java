package com.ektrepha.verification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.verification.dto.response.VerificationDocumentResponse;
import com.ektrepha.verification.service.NannyVerificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Nanny document verification: a NANNY submits one document per {@link VerificationDocType}, an
 * ADMIN reviews it. {@code /documents/{id}/approve} is public (no bearer token required) per
 * explicit ask - for local testing only, see SecurityConfig.
 */
@Tag(name = "Nanny Verification", description = "Nanny document submission and admin approval.")
@RestController
@RequestMapping("/api/v1/nanny-verification")
@RequiredArgsConstructor
public class NannyVerificationController {

	private final NannyVerificationService nannyVerificationService;

	@Operation(summary = "Submit a verification document", description = "Uploads a document (JPEG/PNG/WEBP/PDF) for the caller's own nanny profile as PENDING.")
	@PostMapping("/documents")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<VerificationDocumentResponse> uploadDocument(Authentication authentication,
			@Parameter(description = "Which kind of document this is") @RequestParam("type") VerificationDocType type,
			@RequestParam("file") MultipartFile file) {
		NannyVerification record = nannyVerificationService.submitDocument(userId(authentication), type, file);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record));
	}

	@Operation(summary = "Approve a verification document", description = "Marks one document VERIFIED and recomputes the owning nanny's rollup. Public: no bearer token required (see SecurityConfig).")
	@PostMapping("/documents/{id}/approve")
	public ResponseEntity<VerificationDocumentResponse> approveDocument(Authentication authentication,
			@Parameter(description = "nanny_verification row id") @PathVariable Long id) {
		NannyVerification record = nannyVerificationService.updateRecordStatus(id, VerificationRecordStatus.VERIFIED, reviewerId(authentication), null);
		return ResponseEntity.ok(toResponse(record));
	}

	private VerificationDocumentResponse toResponse(NannyVerification record) {
		return new VerificationDocumentResponse(record.getId(), record.getNanny().getId(), record.getType().name(),
				record.getStatus().name(), record.getCreatedAt(), record.getReviewedAt());
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

	// approveDocument is public, so authentication may be anonymous ("anonymousUser") rather than a
	// real JWT-backed principal - fall back to no reviewer instead of failing to parse the name.
	private Long reviewerId(Authentication authentication) {
		try {
			return authentication == null ? null : Long.valueOf(authentication.getName());
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
