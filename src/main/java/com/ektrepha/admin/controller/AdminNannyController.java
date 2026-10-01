package com.ektrepha.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.admin.dto.request.AdminNannyCreateRequest;
import com.ektrepha.admin.dto.request.AdminNannyUpdateRequest;
import com.ektrepha.admin.dto.response.AdminBookingListResponse;
import com.ektrepha.admin.dto.response.AdminNannyDetailResponse;
import com.ektrepha.admin.dto.response.AdminNannyListResponse;
import com.ektrepha.admin.dto.response.AdminNannyReviewListResponse;
import com.ektrepha.admin.dto.response.AdminNannyVerificationDocumentResponse;
import com.ektrepha.admin.service.AdminNannyService;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.VerificationDocType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize.
@RestController
@RequestMapping("/api/v1/admin/nannies")
@RequiredArgsConstructor
public class AdminNannyController {

	private final AdminNannyService adminNannyService;

	@GetMapping
	public ResponseEntity<AdminNannyListResponse> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) NannyVerificationStatus verificationStatus,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Long zoneId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminNannyService.list(q, verificationStatus, active, zoneId, page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AdminNannyDetailResponse> detail(@PathVariable Long id) {
		return ResponseEntity.ok(adminNannyService.detail(id));
	}

	@PostMapping
	public ResponseEntity<AdminNannyDetailResponse> create(@Valid @RequestBody AdminNannyCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminNannyService.create(request));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<AdminNannyDetailResponse> update(@PathVariable Long id, @RequestBody AdminNannyUpdateRequest request) {
		return ResponseEntity.ok(adminNannyService.update(id, request));
	}

	@GetMapping("/{id}/documents")
	public ResponseEntity<List<AdminNannyVerificationDocumentResponse>> documents(@PathVariable Long id) {
		return ResponseEntity.ok(adminNannyService.documents(id));
	}

	// Admin uploading a document on the nanny's behalf (e.g. collected in person) — lands as PENDING,
	// same as the nanny's own /api/v1/nanny-verification/documents submission, reviewed the same way.
	@PostMapping("/{id}/documents")
	public ResponseEntity<AdminNannyVerificationDocumentResponse> uploadDocument(@PathVariable Long id,
			@RequestParam("type") VerificationDocType type, @RequestParam("file") MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminNannyService.uploadDocument(id, type, file));
	}

	// Stands in for a "Roster" tab until a real schedule table exists — see AdminNannyDetailResponse's javadoc.
	@GetMapping("/{id}/roster")
	public ResponseEntity<AdminBookingListResponse> roster(@PathVariable Long id,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminNannyService.roster(id, page, size));
	}

	@GetMapping("/{id}/reviews")
	public ResponseEntity<AdminNannyReviewListResponse> reviews(@PathVariable Long id,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminNannyService.reviews(id, page, size));
	}

}
