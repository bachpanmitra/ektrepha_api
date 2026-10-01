package com.ektrepha.serviceability.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.serviceability.dto.request.ZoneCreateRequest;
import com.ektrepha.serviceability.dto.request.ZoneStatusRequest;
import com.ektrepha.serviceability.dto.request.ZoneUpdateRequest;
import com.ektrepha.serviceability.dto.response.PincodeResponse;
import com.ektrepha.serviceability.dto.response.ZoneResponse;
import com.ektrepha.serviceability.service.PincodeService;
import com.ektrepha.serviceability.service.ZoneService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize.
@RestController
@RequestMapping("/api/v1/admin/zones")
@RequiredArgsConstructor
public class AdminZoneController {

	private final ZoneService zoneService;
	private final PincodeService pincodeService;

	@GetMapping
	public ResponseEntity<List<ZoneResponse>> list() {
		return ResponseEntity.ok(zoneService.listZones());
	}

	// No pincode listing existed anywhere before this — backs the admin Zones & Pricing screen's
	// per-zone pincode table.
	@GetMapping("/{id}/pincodes")
	public ResponseEntity<List<PincodeResponse>> listPincodes(@PathVariable Long id) {
		return ResponseEntity.ok(pincodeService.listByZone(id));
	}

	@PostMapping
	public ResponseEntity<ZoneResponse> create(@Valid @RequestBody ZoneCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.createZone(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ZoneResponse> update(@PathVariable Long id, @Valid @RequestBody ZoneUpdateRequest request) {
		return ResponseEntity.ok(zoneService.updateZone(id, request));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<ZoneResponse> setStatus(@PathVariable Long id, @Valid @RequestBody ZoneStatusRequest request) {
		return ResponseEntity.ok(zoneService.setZoneStatus(id, request));
	}

}
