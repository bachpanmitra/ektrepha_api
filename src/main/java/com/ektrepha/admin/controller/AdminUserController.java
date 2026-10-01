package com.ektrepha.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.admin.dto.request.AdminUserCreateRequest;
import com.ektrepha.admin.dto.request.AdminUserUpdateRequest;
import com.ektrepha.admin.dto.response.AdminUserSummaryResponse;
import com.ektrepha.admin.service.AdminUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize. Any admin can manage
// any other admin's active status — there is no separate "super-admin" tier (see SelfAccountLockoutException
// for the one safeguard: an admin can't deactivate themselves).
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

	private final AdminUserService adminUserService;

	@GetMapping
	public ResponseEntity<List<AdminUserSummaryResponse>> list(Authentication authentication) {
		return ResponseEntity.ok(adminUserService.list(userId(authentication)));
	}

	@PostMapping
	public ResponseEntity<AdminUserSummaryResponse> create(@Valid @RequestBody AdminUserCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.create(request));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<AdminUserSummaryResponse> update(Authentication authentication, @PathVariable Long id,
			@RequestBody AdminUserUpdateRequest request) {
		return ResponseEntity.ok(adminUserService.update(id, userId(authentication), request));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
