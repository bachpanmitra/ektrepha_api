package com.ektrepha.ops.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.model.CaregiverRole;
import com.ektrepha.ops.dto.response.OpsApprovalItem;
import com.ektrepha.ops.dto.response.OpsHelperDetailResponse;
import com.ektrepha.ops.dto.response.OpsTeamMemberResponse;
import com.ektrepha.ops.dto.response.OpsTeamTodayResponse;
import com.ektrepha.ops.service.OpsTeamService;

import lombok.RequiredArgsConstructor;

/**
 * The ops manager app: the signed-in manager's own team only (nanny.manager_user_id = them).
 * Approving/rejecting still goes through the existing /api/v1/admin/approvals/** and
 * /api/v1/admin/safety/** endpoints, using the ids these responses return.
 */
@RestController
@RequestMapping("/api/v1/ops")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class OpsTeamController {

	private final OpsTeamService opsTeamService;

	// "Team today": counts, who needs you now, and pending requests - for this manager's team.
	@GetMapping("/team/today")
	public ResponseEntity<OpsTeamTodayResponse> today(Authentication authentication) {
		return ResponseEntity.ok(opsTeamService.today(userId(authentication)));
	}

	// "My team": every helper with today's status, most urgent first. role and group are optional.
	@GetMapping("/team")
	public ResponseEntity<List<OpsTeamMemberResponse>> team(Authentication authentication,
			@RequestParam(required = false) CaregiverRole role, @RequestParam(required = false) String group) {
		return ResponseEntity.ok(opsTeamService.team(userId(authentication), role, group));
	}

	@GetMapping("/team/{nannyId}")
	public ResponseEntity<OpsHelperDetailResponse> helper(Authentication authentication, @PathVariable Long nannyId) {
		return ResponseEntity.ok(opsTeamService.helper(userId(authentication), nannyId));
	}

	@GetMapping("/approvals")
	public ResponseEntity<List<OpsApprovalItem>> approvals(Authentication authentication) {
		return ResponseEntity.ok(opsTeamService.approvals(userId(authentication)));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
