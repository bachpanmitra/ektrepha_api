package com.ektrepha.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.admin.dto.response.AdminParentDetailResponse;
import com.ektrepha.admin.dto.response.AdminParentListResponse;
import com.ektrepha.admin.service.AdminParentService;

import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize. Read-only — Ops has no
// write actions on a parent's own profile.
@RestController
@RequestMapping("/api/v1/admin/parents")
@RequiredArgsConstructor
public class AdminParentController {

	private final AdminParentService adminParentService;

	@GetMapping
	public ResponseEntity<AdminParentListResponse> list(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminParentService.list(q, page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AdminParentDetailResponse> detail(@PathVariable Long id) {
		return ResponseEntity.ok(adminParentService.detail(id));
	}

}
