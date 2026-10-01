package com.ektrepha.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.admin.dto.request.AdminRejectionRequest;
import com.ektrepha.admin.dto.response.AdminAttendanceCorrectionListResponse;
import com.ektrepha.admin.dto.response.AdminAttendanceCorrectionResponse;
import com.ektrepha.admin.dto.response.AdminLeaveRequestListResponse;
import com.ektrepha.admin.dto.response.AdminLeaveRequestResponse;
import com.ektrepha.admin.dto.response.AdminShiftChangeRequestListResponse;
import com.ektrepha.admin.dto.response.AdminShiftChangeRequestResponse;
import com.ektrepha.admin.service.AdminApprovalService;
import com.ektrepha.model.RequestStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Role enforcement for the whole /api/v1/admin/** prefix is centralized in SecurityConfig
// (hasRole('ADMIN')) rather than repeated per-controller with @PreAuthorize.
@RestController
@RequestMapping("/api/v1/admin/approvals")
@RequiredArgsConstructor
public class AdminApprovalController {

	private final AdminApprovalService adminApprovalService;

	@GetMapping("/leave")
	public ResponseEntity<AdminLeaveRequestListResponse> listLeave(
			@RequestParam(required = false) RequestStatus status,
			@RequestParam(required = false) Long nannyId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminApprovalService.listLeaveRequests(status, nannyId, page, size));
	}

	@PostMapping("/leave/{id}/approve")
	public ResponseEntity<AdminLeaveRequestResponse> approveLeave(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(adminApprovalService.approveLeaveRequest(id, userId(authentication)));
	}

	@PostMapping("/leave/{id}/reject")
	public ResponseEntity<AdminLeaveRequestResponse> rejectLeave(Authentication authentication, @PathVariable Long id, @Valid @RequestBody AdminRejectionRequest request) {
		return ResponseEntity.ok(adminApprovalService.rejectLeaveRequest(id, userId(authentication), request));
	}

	@GetMapping("/shift-changes")
	public ResponseEntity<AdminShiftChangeRequestListResponse> listShiftChanges(
			@RequestParam(required = false) RequestStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminApprovalService.listShiftChangeRequests(status, page, size));
	}

	@PostMapping("/shift-changes/{id}/approve")
	public ResponseEntity<AdminShiftChangeRequestResponse> approveShiftChange(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(adminApprovalService.approveShiftChangeRequest(id, userId(authentication)));
	}

	@PostMapping("/shift-changes/{id}/reject")
	public ResponseEntity<AdminShiftChangeRequestResponse> rejectShiftChange(Authentication authentication, @PathVariable Long id, @Valid @RequestBody AdminRejectionRequest request) {
		return ResponseEntity.ok(adminApprovalService.rejectShiftChangeRequest(id, userId(authentication), request));
	}

	@GetMapping("/attendance-corrections")
	public ResponseEntity<AdminAttendanceCorrectionListResponse> listAttendanceCorrections(
			@RequestParam(required = false) RequestStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(adminApprovalService.listAttendanceCorrections(status, page, size));
	}

	@PostMapping("/attendance-corrections/{id}/approve")
	public ResponseEntity<AdminAttendanceCorrectionResponse> approveAttendanceCorrection(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(adminApprovalService.approveAttendanceCorrection(id, userId(authentication)));
	}

	@PostMapping("/attendance-corrections/{id}/reject")
	public ResponseEntity<AdminAttendanceCorrectionResponse> rejectAttendanceCorrection(Authentication authentication, @PathVariable Long id, @Valid @RequestBody AdminRejectionRequest request) {
		return ResponseEntity.ok(adminApprovalService.rejectAttendanceCorrection(id, userId(authentication), request));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
