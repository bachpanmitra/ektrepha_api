package com.ektrepha.serviceability.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.serviceability.dto.request.WaitlistCreateRequest;
import com.ektrepha.serviceability.dto.response.WaitlistResponse;
import com.ektrepha.serviceability.service.WaitlistService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/serviceability/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

	private final WaitlistService waitlistService;

	@PostMapping
	public ResponseEntity<WaitlistResponse> join(@Valid @RequestBody WaitlistCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(waitlistService.join(request));
	}

}
