package com.ektrepha.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.auth.dto.request.EmailLoginRequest;
import com.ektrepha.auth.dto.response.EmailLoginResponse;
import com.ektrepha.auth.dto.request.EmailSignupRequest;
import com.ektrepha.auth.dto.response.EmailSignupResponse;
import com.ektrepha.auth.dto.request.ForgotPasswordRequest;
import com.ektrepha.auth.dto.response.ForgotPasswordResponse;
import com.ektrepha.auth.dto.request.GoogleLoginRequest;
import com.ektrepha.auth.dto.response.GoogleLoginResponse;
import com.ektrepha.auth.dto.request.GoogleSignupRequest;
import com.ektrepha.auth.dto.response.GoogleSignupResponse;
import com.ektrepha.auth.dto.response.MessageResponse;
import com.ektrepha.auth.dto.request.PhoneLoginRequest;
import com.ektrepha.auth.dto.response.PhoneLoginResponse;
import com.ektrepha.auth.dto.request.PhoneSignupInitiateRequest;
import com.ektrepha.auth.dto.response.PhoneSignupInitiateResponse;
import com.ektrepha.auth.dto.request.PhoneSignupVerifyRequest;
import com.ektrepha.auth.dto.response.PhoneSignupVerifyResponse;
import com.ektrepha.auth.dto.request.RefreshRequest;
import com.ektrepha.auth.dto.request.RegisterRequest;
import com.ektrepha.auth.dto.response.RegisterResponse;
import com.ektrepha.auth.dto.request.ResetPasswordRequest;
import com.ektrepha.auth.dto.response.ResetPasswordResponse;
import com.ektrepha.auth.dto.response.TokenPairResponse;
import com.ektrepha.auth.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	// ---------------------------------------------------------------- Sign up

	@PostMapping("/signup/google")
	public ResponseEntity<GoogleSignupResponse> signupGoogle(@Valid @RequestBody GoogleSignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupGoogle(request));
	}

	@PostMapping("/signup/phone/initiate")
	public ResponseEntity<PhoneSignupInitiateResponse> initiatePhoneSignup(@Valid @RequestBody PhoneSignupInitiateRequest request) {
		return ResponseEntity.ok(authService.initiatePhoneSignup(request));
	}

	@PostMapping("/signup/phone/verify")
	public ResponseEntity<PhoneSignupVerifyResponse> verifyPhoneSignup(@Valid @RequestBody PhoneSignupVerifyRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.verifyPhoneSignup(request));
	}

	@PostMapping("/signup/email")
	public ResponseEntity<EmailSignupResponse> signupEmail(@Valid @RequestBody EmailSignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupEmail(request));
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	// ------------------------------------------------------------------ Login

	@PostMapping("/login/google")
	public ResponseEntity<GoogleLoginResponse> loginGoogle(@Valid @RequestBody GoogleLoginRequest request) {
		return ResponseEntity.ok(authService.loginGoogle(request));
	}

	@PostMapping("/login/phone")
	public ResponseEntity<PhoneLoginResponse> loginPhone(@Valid @RequestBody PhoneLoginRequest request) {
		return ResponseEntity.ok(authService.loginPhone(request));
	}

	@PostMapping("/login/email")
	public ResponseEntity<EmailLoginResponse> loginEmail(@Valid @RequestBody EmailLoginRequest request) {
		return ResponseEntity.ok(authService.loginEmail(request));
	}

	// -------------------------------------------------------- Session lifecycle

	@PostMapping("/refresh")
	public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ResponseEntity.ok(authService.refresh(request));
	}

	@PostMapping("/logout")
	public ResponseEntity<MessageResponse> logout(@Valid @RequestBody RefreshRequest request) {
		return ResponseEntity.ok(authService.logout(request));
	}

	// -------------------------------------------------------------- Password

	@PostMapping("/password/forgot")
	public ResponseEntity<ForgotPasswordResponse> forgotPassword(@RequestBody ForgotPasswordRequest request) {
		return ResponseEntity.ok(authService.forgotPassword(request));
	}

	@PostMapping("/password/reset")
	public ResponseEntity<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		return ResponseEntity.ok(authService.resetPassword(request));
	}

}
