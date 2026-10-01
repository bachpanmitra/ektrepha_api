package com.ektrepha.admin.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.admin.dto.request.AdminResolutionRequest;
import com.ektrepha.admin.dto.response.AdminIncidentReportListResponse;
import com.ektrepha.admin.dto.response.AdminIncidentReportResponse;
import com.ektrepha.admin.dto.response.AdminSosAlertResponse;
import com.ektrepha.admin.service.AdminSafetyService;
import com.ektrepha.model.IncidentStatus;

import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize.
@RestController
@RequestMapping("/api/v1/admin/safety")
@RequiredArgsConstructor
public class AdminSafetyController {

	private final AdminSafetyService adminSafetyService;

	@GetMapping("/sos")
	public ResponseEntity<List<AdminSosAlertResponse>> listOpenSos() {
		return ResponseEntity.ok(adminSafetyService.listOpenSos());
	}

	@PostMapping("/sos/{id}/acknowledge")
	public ResponseEntity<AdminSosAlertResponse> acknowledgeSos(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(adminSafetyService.acknowledgeSos(id, userId(authentication)));
	}

	@PostMapping("/sos/{id}/resolve")
	public ResponseEntity<AdminSosAlertResponse> resolveSos(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(adminSafetyService.resolveSos(id, userId(authentication)));
	}

	@GetMapping("/incidents")
	public ResponseEntity<AdminIncidentReportListResponse> listIncidents(
			@RequestParam(required = false) IncidentStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminSafetyService.listIncidents(status, page, size));
	}

	@PostMapping("/incidents/{id}/resolve")
	public ResponseEntity<AdminIncidentReportResponse> resolveIncident(Authentication authentication, @PathVariable Long id, @RequestBody(required = false) AdminResolutionRequest request) {
		AdminResolutionRequest body = request != null ? request : new AdminResolutionRequest(null);
		return ResponseEntity.ok(adminSafetyService.resolveIncident(id, userId(authentication), body));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
