package com.ektrepha.auth.staff.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.auth.staff.dto.request.StaffOtpRequestRequest;
import com.ektrepha.auth.staff.dto.request.StaffOtpVerifyRequest;
import com.ektrepha.auth.staff.dto.response.StaffOtpRequestResponse;
import com.ektrepha.auth.staff.dto.response.StaffSessionResponse;
import com.ektrepha.auth.staff.service.StaffAuthService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Mobile OTP login for the nanny app (ektrepha-nanny-ui) — unauthenticated, like every other
 * {@code /api/v1/auth/**} route (see SecurityConfig). Distinct from {@code /api/v1/auth/mobile/otp/**},
 * the parent app's equivalent: this one never auto-creates an account, since nanny accounts are
 * provisioned by ops rather than self-signup.
 */
@Tag(name = "Staff Auth", description = "Nanny-app mobile OTP login.")
@RestController
@RequestMapping("/api/v1/auth/staff/otp")
@RequiredArgsConstructor
public class StaffAuthController {

	private final StaffAuthService staffAuthService;

	@PostMapping("/request")
	public ResponseEntity<StaffOtpRequestResponse> requestOtp(@Valid @RequestBody StaffOtpRequestRequest request) {
		return ResponseEntity.ok(staffAuthService.requestOtp(request));
	}

	@PostMapping("/verify")
	public ResponseEntity<StaffSessionResponse> verifyOtp(@Valid @RequestBody StaffOtpVerifyRequest request) {
		return ResponseEntity.ok(staffAuthService.verifyOtp(request));
	}

}
