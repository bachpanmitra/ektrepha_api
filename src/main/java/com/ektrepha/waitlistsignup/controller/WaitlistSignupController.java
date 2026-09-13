package com.ektrepha.waitlistsignup.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.waitlistsignup.dto.request.WaitlistSignupRequest;
import com.ektrepha.waitlistsignup.dto.response.WaitlistSignupResponse;
import com.ektrepha.waitlistsignup.service.WaitlistSignupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({ "/api/waitlist", "/api/v1/waitlist" })
@RequiredArgsConstructor
public class WaitlistSignupController {

	private final WaitlistSignupService waitlistSignupService;

	@PostMapping
	public ResponseEntity<WaitlistSignupResponse> join(@Valid @RequestBody WaitlistSignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(waitlistSignupService.join(request));
	}

}
