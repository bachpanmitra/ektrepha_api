package com.ektrepha.nanny.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.nanny.dto.response.NannyPublicProfileResponse;
import com.ektrepha.nanny.dto.response.NannyReviewListResponse;
import com.ektrepha.nanny.dto.response.VerificationSummaryResponse;
import com.ektrepha.nanny.service.NannyProfileService;

import lombok.RequiredArgsConstructor;

/** S1/S2 — public (to any authenticated parent) read-only nanny profile and verification summary. Never serializes {@code s3Key}/{@code vendorReferenceId}/{@code reviewedBy}/{@code rejectionReason}. */
@RestController
@RequestMapping("/api/v1/nannies")
@RequiredArgsConstructor
public class NannyProfileController {

	private final NannyProfileService nannyProfileService;

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<NannyPublicProfileResponse> getProfile(@PathVariable Long id) {
		return ResponseEntity.ok(nannyProfileService.getProfile(id));
	}

	@GetMapping("/{id}/verification")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<VerificationSummaryResponse> getVerification(@PathVariable Long id) {
		return ResponseEntity.ok(nannyProfileService.getVerification(id));
	}

	@GetMapping("/{id}/reviews")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<NannyReviewListResponse> listReviews(@PathVariable Long id,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ResponseEntity.ok(nannyProfileService.listReviews(id, page, pageSize));
	}

}
