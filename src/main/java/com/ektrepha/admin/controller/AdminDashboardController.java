package com.ektrepha.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.admin.dto.response.AdminDashboardTodayResponse;
import com.ektrepha.admin.service.AdminDashboardService;

import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize.
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

	private final AdminDashboardService adminDashboardService;

	@GetMapping("/today")
	public ResponseEntity<AdminDashboardTodayResponse> today() {
		return ResponseEntity.ok(adminDashboardService.today());
	}

}
