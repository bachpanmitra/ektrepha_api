package com.ektrepha.serviceability.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.serviceability.dto.request.PincodeCreateRequest;
import com.ektrepha.serviceability.dto.request.PincodeStatusRequest;
import com.ektrepha.serviceability.dto.response.PincodeBulkImportResponse;
import com.ektrepha.serviceability.dto.response.PincodeResponse;
import com.ektrepha.serviceability.service.PincodeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/pincodes")
@RequiredArgsConstructor
public class AdminPincodeController {

	private final PincodeService pincodeService;

	@PostMapping
	public ResponseEntity<PincodeResponse> add(@Valid @RequestBody PincodeCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(pincodeService.addPincode(request));
	}

	@PutMapping("/{id}/status")
	public ResponseEntity<PincodeResponse> setStatus(@PathVariable Long id, @Valid @RequestBody PincodeStatusRequest request) {
		return ResponseEntity.ok(pincodeService.setStatus(id, request));
	}

	@PostMapping("/bulk-import")
	public ResponseEntity<PincodeBulkImportResponse> bulkImport(@RequestParam("file") MultipartFile file) {
		return ResponseEntity.ok(pincodeService.bulkImport(file));
	}

}
