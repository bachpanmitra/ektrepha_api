package com.ektrepha.caremanager.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.CmCallerResolver;
import com.ektrepha.caremanager.dto.request.RaiseComplaintRequest;
import com.ektrepha.caremanager.dto.request.UpdateComplaintRequest;
import com.ektrepha.caremanager.dto.response.ComplaintPageResponse;
import com.ektrepha.caremanager.dto.response.ComplaintResponse;
import com.ektrepha.caremanager.service.CmComplaintService;
import com.ektrepha.model.ComplaintStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "CM - complaints" */
@RestController
@RequestMapping("/v1/cm/complaints")
@RequiredArgsConstructor
public class CmComplaintController {

	private final CmCallerResolver cmCallerResolver;
	private final CmComplaintService cmComplaintService;

	@GetMapping
	public ComplaintPageResponse list(
			@RequestParam(required = false) ComplaintStatus status,
			@RequestParam(defaultValue = "true") boolean mine,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer limit,
			Authentication authentication) {
		return cmComplaintService.list(cmCallerResolver.resolve(authentication), status, mine, cursor, limit);
	}

	@PostMapping
	public ResponseEntity<ComplaintResponse> raise(@Valid @RequestBody RaiseComplaintRequest request, Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED).body(cmComplaintService.raise(cmCallerResolver.resolve(authentication), request));
	}

	@PatchMapping("/{complaintId}")
	public ComplaintResponse update(@PathVariable String complaintId, @RequestBody UpdateComplaintRequest request,
			Authentication authentication) {
		return cmComplaintService.update(cmCallerResolver.resolve(authentication), complaintId, request);
	}

}
