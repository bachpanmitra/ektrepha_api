package com.ektrepha.ops.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.ops.dto.request.TeamAssignmentRequest;
import com.ektrepha.ops.dto.response.TeamAssignmentResponse;
import com.ektrepha.ops.service.OpsTeamService;

import lombok.RequiredArgsConstructor;

/** Puts a helper on an ops manager's team and sets their role. ADMIN-only via SecurityConfig's /api/v1/admin/** rule. */
@RestController
@RequestMapping("/api/v1/admin/nannies")
@RequiredArgsConstructor
public class AdminTeamAssignmentController {

	private final OpsTeamService opsTeamService;

	@PutMapping("/{id}/team")
	public ResponseEntity<TeamAssignmentResponse> assign(@PathVariable Long id, @RequestBody TeamAssignmentRequest request) {
		return ResponseEntity.ok(opsTeamService.assign(id, request));
	}

}
