package com.ektrepha.workforce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.workforce.dto.request.LeaveRequestCreateRequest;
import com.ektrepha.workforce.dto.response.RequestCreatedResponse;
import com.ektrepha.workforce.service.WorkforceRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Nanny-app leave requests — reviewed by Ops in the admin portal's Approvals tab. */
@RestController
@RequestMapping("/api/v1/nanny-leave")
@RequiredArgsConstructor
public class NannyLeaveController {

	private final WorkforceRequestService workforceRequestService;

	@PostMapping
	public ResponseEntity<RequestCreatedResponse> request(Authentication authentication, @Valid @RequestBody LeaveRequestCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(workforceRequestService.requestLeave(userId(authentication), request));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
