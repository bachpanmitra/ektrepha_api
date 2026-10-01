package com.ektrepha.workforce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.workforce.dto.request.IncidentReportCreateRequest;
import com.ektrepha.workforce.dto.response.RequestCreatedResponse;
import com.ektrepha.workforce.service.WorkforceRequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Either a NANNY or a PARENT can file an incident report — reviewed by Ops in the admin portal's Safety tab. */
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentReportController {

	private final WorkforceRequestService workforceRequestService;

	@PostMapping
	public ResponseEntity<RequestCreatedResponse> report(Authentication authentication, @Valid @RequestBody IncidentReportCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(workforceRequestService.reportIncident(userId(authentication), request));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
