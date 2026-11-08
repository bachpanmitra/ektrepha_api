package com.ektrepha.workforce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.workforce.dto.request.ArrivedRequest;
import com.ektrepha.workforce.dto.request.LocationPingRequest;
import com.ektrepha.workforce.dto.request.StartTripRequest;
import com.ektrepha.workforce.dto.response.ArrivedResponse;
import com.ektrepha.workforce.dto.response.NannyEtaResponse;
import com.ektrepha.workforce.dto.response.StartTripResponse;
import com.ektrepha.workforce.service.NannyTripService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * "On the way" live-location tracking (API Map rows 30, 31, 51, 52) — a separate controller on the
 * same {@code /api/v1/nanny-bookings} prefix as {@link NannyBookingRequestController}, same
 * reasoning as that class's own comment: keeps this feature area isolated from the others sharing
 * the prefix. SecurityConfig's existing blanket {@code POST/GET /api/v1/nanny-bookings/** -&gt;
 * hasRole('NANNY')} matchers already cover every method here.
 */
@RestController
@RequestMapping("/api/v1/nanny-bookings")
@RequiredArgsConstructor
public class NannyTripController {

	private final NannyTripService nannyTripService;

	@PostMapping("/{id}/start-trip")
	public ResponseEntity<StartTripResponse> startTrip(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody StartTripRequest request) {
		return ResponseEntity.ok(nannyTripService.startTrip(userId(authentication), id, request.lat(), request.lng()));
	}

	@PostMapping("/{id}/arrived")
	public ResponseEntity<ArrivedResponse> arrived(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody ArrivedRequest request) {
		return ResponseEntity.ok(nannyTripService.arrived(userId(authentication), id, request.lat(), request.lng(), request.accuracyM()));
	}

	@PostMapping("/{id}/location")
	public ResponseEntity<Void> location(Authentication authentication, @PathVariable Long id,
			@Valid @RequestBody LocationPingRequest request) {
		nannyTripService.reportLocation(userId(authentication), id, request.lat(), request.lng(), request.accuracyM(), request.recordedAt());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/eta")
	public ResponseEntity<NannyEtaResponse> eta(Authentication authentication, @PathVariable Long id) {
		NannyEtaResponse response = nannyTripService.nannyEta(userId(authentication), id);
		return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
