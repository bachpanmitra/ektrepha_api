package com.ektrepha.caremanager.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.dto.request.LogContactRequest;
import com.ektrepha.caremanager.dto.request.RateVisitRequest;
import com.ektrepha.caremanager.dto.request.ReasonRequest;
import com.ektrepha.caremanager.dto.response.ChangeRequestResponse;
import com.ektrepha.caremanager.dto.response.MyCareManagerResponse;
import com.ektrepha.caremanager.dto.response.RateVisitResponse;
import com.ektrepha.caremanager.service.ParentCareManagerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "Parent app" — role PARENT; the parent id is always taken from the token, never the path. */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ParentCareManagerController {

	private final ParentCareManagerService parentCareManagerService;

	@GetMapping("/me/care-manager")
	public ResponseEntity<MyCareManagerResponse> getMyCareManager(Authentication authentication) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
				.body(parentCareManagerService.getMyCareManager(userId(authentication)));
	}

	@PostMapping("/me/care-manager/contacts")
	public ResponseEntity<Void> logContact(@Valid @RequestBody LogContactRequest request, Authentication authentication) {
		parentCareManagerService.logContact(userId(authentication), request);
		return ResponseEntity.status(HttpStatus.ACCEPTED).build();
	}

	@PostMapping("/me/care-manager/change-requests")
	public ResponseEntity<ChangeRequestResponse> requestCareManagerChange(@RequestBody(required = false) ReasonRequest request,
			Authentication authentication) {
		ReasonRequest body = request == null ? new ReasonRequest(null) : request;
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(parentCareManagerService.requestCareManagerChange(userId(authentication), body));
	}

	@PostMapping("/bookings/{bookingId}/ratings")
	public ResponseEntity<RateVisitResponse> rateVisit(@PathVariable String bookingId, @Valid @RequestBody RateVisitRequest request,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(parentCareManagerService.rateVisit(userId(authentication), bookingId, request));
	}

	@PostMapping("/bookings/{bookingId}/nanny-change-requests")
	public ResponseEntity<ChangeRequestResponse> requestNannyChange(@PathVariable String bookingId,
			@RequestBody(required = false) ReasonRequest request, Authentication authentication) {
		ReasonRequest body = request == null ? new ReasonRequest(null) : request;
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(parentCareManagerService.requestNannyChange(userId(authentication), bookingId, body));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
