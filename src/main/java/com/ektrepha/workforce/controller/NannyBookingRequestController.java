package com.ektrepha.workforce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.workforce.dto.request.ReasonRequest;
import com.ektrepha.workforce.dto.response.RequestCreatedResponse;
import com.ektrepha.workforce.service.WorkforceRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * A separate controller on the same {@code /api/v1/nanny-bookings} prefix as
 * {@link com.ektrepha.booking.controller.NannyBookingController} (Spring routes both fine — the
 * sub-paths don't collide) rather than adding methods there, to keep this Approvals-phase addition
 * out of that controller's own active development.
 */
@RestController
@RequestMapping("/api/v1/nanny-bookings")
@RequiredArgsConstructor
public class NannyBookingRequestController {

	private final WorkforceRequestService workforceRequestService;

	@PostMapping("/{id}/shift-change-request")
	public ResponseEntity<RequestCreatedResponse> requestShiftChange(Authentication authentication,
			@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(workforceRequestService.requestShiftChange(userId(authentication), id, request));
	}

	@PostMapping("/{id}/attendance-correction")
	public ResponseEntity<RequestCreatedResponse> requestAttendanceCorrection(Authentication authentication,
			@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(workforceRequestService.requestAttendanceCorrection(userId(authentication), id, request));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
