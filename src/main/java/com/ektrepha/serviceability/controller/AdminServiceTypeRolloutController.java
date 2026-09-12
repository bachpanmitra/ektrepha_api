package com.ektrepha.serviceability.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.serviceability.dto.request.ServiceTypeRolloutRequest;
import com.ektrepha.serviceability.dto.response.ServiceTypeRolloutResponse;
import com.ektrepha.serviceability.service.ServiceTypeRolloutService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/zones/{zoneId}/service-types/{serviceTypeId}")
@RequiredArgsConstructor
public class AdminServiceTypeRolloutController {

	private final ServiceTypeRolloutService serviceTypeRolloutService;

	@PatchMapping
	public ResponseEntity<ServiceTypeRolloutResponse> setStatus(@PathVariable Long zoneId, @PathVariable Long serviceTypeId,
			@Valid @RequestBody ServiceTypeRolloutRequest request) {
		return ResponseEntity.ok(serviceTypeRolloutService.setStatus(zoneId, serviceTypeId, request));
	}

}
