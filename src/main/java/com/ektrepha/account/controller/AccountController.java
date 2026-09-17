package com.ektrepha.account.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.account.dto.request.AccountDeleteRequest;
import com.ektrepha.account.dto.request.EmailChangeRequest;
import com.ektrepha.account.dto.request.EmailChangeVerifyRequest;
import com.ektrepha.account.dto.request.PhoneChangeRequest;
import com.ektrepha.account.dto.request.SetPasswordRequest;
import com.ektrepha.account.dto.response.LoginMethodsResponse;
import com.ektrepha.account.dto.response.OtpSentResponse;
import com.ektrepha.account.service.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** A2 (email/phone change), A3 (verify), A4 (login methods, set/change password), A6 (delete). A1/A2's name field lives on {@code UserController} instead (a plain PUT, no verification needed). */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class AccountController {

	private final AccountService accountService;

	@PostMapping("/email/change")
	public ResponseEntity<OtpSentResponse> requestEmailChange(Authentication authentication, @Valid @RequestBody EmailChangeRequest request) {
		OtpSentResponse response = accountService.requestEmailChange(userId(authentication), request.newEmail());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

	@PostMapping("/email/verify")
	public ResponseEntity<Void> confirmEmailChange(Authentication authentication, @Valid @RequestBody EmailChangeVerifyRequest request) {
		accountService.confirmEmailChange(userId(authentication), request.newEmail(), request.otp());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/phone/change")
	public ResponseEntity<Void> changePhone(Authentication authentication, @Valid @RequestBody PhoneChangeRequest request) {
		accountService.changePhone(userId(authentication), request.firebaseIdToken());
		return ResponseEntity.ok().build();
	}

	@GetMapping("/login-methods")
	public ResponseEntity<LoginMethodsResponse> loginMethods(Authentication authentication) {
		return ResponseEntity.ok(accountService.getLoginMethods(userId(authentication)));
	}

	@PostMapping("/password")
	public ResponseEntity<Void> setPassword(Authentication authentication, @Valid @RequestBody SetPasswordRequest request) {
		accountService.setPassword(userId(authentication), request.currentPassword(), request.newPassword());
		return ResponseEntity.ok().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> deleteAccount(Authentication authentication, @Valid @RequestBody AccountDeleteRequest request) {
		accountService.deleteAccount(userId(authentication), request.confirmation());
		return ResponseEntity.noContent().build();
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
