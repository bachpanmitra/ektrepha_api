package com.ektrepha.verification.controller;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.verification.dto.request.VerificationRejectRequest;
import com.ektrepha.verification.dto.response.VerificationDocumentResponse;
import com.ektrepha.verification.service.NannyVerificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Nanny document verification: a NANNY submits one document per {@link VerificationDocType}, an
 * ADMIN reviews it. This is one of several onboarding-signal controllers under
 * {@code /api/v1/nanny-verification/**} that together feed the rollup - see also
 * {@code NannyReferenceController}, {@code NannyInterviewController}, {@code NannyTrainingController}
 * and {@code NannyCodeOfConductController}.
 */
@Tag(name = "Nanny Verification", description = "Nanny document submission and admin approval.")
@RestController
@RequestMapping("/api/v1/nanny-verification")
@RequiredArgsConstructor
public class NannyVerificationController {

	private final NannyVerificationService nannyVerificationService;

	@Operation(summary = "Submit a verification document", description = "Uploads a document (JPEG/PNG/WEBP/PDF) for the caller's own nanny profile as PENDING. expiryDate is only meaningful for BACKGROUND_CHECK (the PCC).")
	@PostMapping("/documents")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<VerificationDocumentResponse> uploadDocument(Authentication authentication,
			@Parameter(description = "Which kind of document this is") @RequestParam("type") VerificationDocType type,
			@RequestParam("file") MultipartFile file,
			@Parameter(description = "Police Clearance Certificate expiry date - required for BACKGROUND_CHECK, ignored otherwise")
			@RequestParam(value = "expiryDate", required = false) LocalDate expiryDate,
			@Parameter(description = "The mobile app's own device identifier - checked for ban evasion and recorded on the nanny")
			@RequestParam(value = "deviceId", required = false) String deviceId) {
		NannyVerification record = nannyVerificationService.submitDocument(userId(authentication), type, file, expiryDate, deviceId);
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record));
	}

	@Operation(summary = "Approve a verification document", description = "Marks one document VERIFIED and recomputes the owning nanny's rollup.")
	@PostMapping("/documents/{id}/approve")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<VerificationDocumentResponse> approveDocument(Authentication authentication,
			@Parameter(description = "nanny_verification row id") @PathVariable Long id) {
		NannyVerification record = nannyVerificationService.updateRecordStatus(id, VerificationRecordStatus.VERIFIED, userId(authentication), null);
		return ResponseEntity.ok(toResponse(record));
	}

	@Operation(summary = "Reject a verification document", description = "Marks one document REJECTED with a reason and recomputes the owning nanny's rollup.")
	@PostMapping("/documents/{id}/reject")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<VerificationDocumentResponse> rejectDocument(Authentication authentication,
			@Parameter(description = "nanny_verification row id") @PathVariable Long id,
			@Valid @RequestBody VerificationRejectRequest request) {
		NannyVerification record = nannyVerificationService.updateRecordStatus(id, VerificationRecordStatus.REJECTED, userId(authentication), request.reason());
		return ResponseEntity.ok(toResponse(record));
	}

	private VerificationDocumentResponse toResponse(NannyVerification record) {
		return new VerificationDocumentResponse(record.getId(), record.getNanny().getId(), record.getType().name(),
				record.getStatus().name(), record.getCreatedAt(), record.getReviewedAt(), record.getExpiryDate());
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
