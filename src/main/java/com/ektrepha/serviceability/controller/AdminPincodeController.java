package com.ektrepha.serviceability.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.serviceability.dto.request.PincodeBulkStatusRequest;
import com.ektrepha.serviceability.dto.request.PincodeCreateRequest;
import com.ektrepha.serviceability.dto.request.PincodeStatusRequest;
import com.ektrepha.serviceability.dto.response.PincodeBulkImportResponse;
import com.ektrepha.serviceability.dto.response.PincodeBulkStatusResponse;
import com.ektrepha.serviceability.dto.response.PincodeListResponse;
import com.ektrepha.serviceability.dto.response.PincodeResponse;
import com.ektrepha.serviceability.service.PincodeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/pincodes")
@RequiredArgsConstructor
public class AdminPincodeController {

	private final PincodeService pincodeService;

	// Standalone "All Pincodes" admin screen — every pincode across every zone, searchable/filterable.
	@GetMapping
	public ResponseEntity<PincodeListResponse> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) ServiceabilityStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(pincodeService.list(q, status, page, size));
	}

	@PostMapping
	public ResponseEntity<PincodeResponse> add(@Valid @RequestBody PincodeCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(pincodeService.addPincode(request));
	}

	@PutMapping("/{id}/status")
	public ResponseEntity<PincodeResponse> setStatus(@PathVariable Long id, @Valid @RequestBody PincodeStatusRequest request) {
		return ResponseEntity.ok(pincodeService.setStatus(id, request));
	}

	// Bulk status change (e.g. "deactivate" = set every selected id to NOT_PLANNED) for the All
	// Pincodes screen's multi-select — reuses setStatus's single-row logic so cache eviction stays
	// consistent; one id that doesn't resolve is reported in `errors`, not fatal to the rest.
	@PostMapping("/bulk-status")
	public ResponseEntity<PincodeBulkStatusResponse> bulkSetStatus(@Valid @RequestBody PincodeBulkStatusRequest request) {
		return ResponseEntity.ok(pincodeService.bulkSetStatus(request));
	}

	@PostMapping("/bulk-import")
	public ResponseEntity<PincodeBulkImportResponse> bulkImport(@RequestParam("file") MultipartFile file) {
		return ResponseEntity.ok(pincodeService.bulkImport(file));
	}

}
