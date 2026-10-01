package com.ektrepha.workforce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.workforce.dto.request.SosRaiseRequest;
import com.ektrepha.workforce.dto.response.RequestCreatedResponse;
import com.ektrepha.workforce.service.WorkforceRequestService;

import lombok.RequiredArgsConstructor;

/** Nanny-app SOS button — surfaces in the admin portal's Safety tab (and the Today dashboard's SOS banner) the moment it's raised. */
@RestController
@RequestMapping("/api/v1/nanny-sos")
@RequiredArgsConstructor
public class NannySosController {

	private final WorkforceRequestService workforceRequestService;

	@PostMapping
	public ResponseEntity<RequestCreatedResponse> raise(Authentication authentication, @RequestBody(required = false) SosRaiseRequest request) {
		SosRaiseRequest body = request != null ? request : new SosRaiseRequest(null, null, null, null);
		return ResponseEntity.status(HttpStatus.CREATED).body(workforceRequestService.raiseSos(userId(authentication), body));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
