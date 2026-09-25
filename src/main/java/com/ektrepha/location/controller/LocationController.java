package com.ektrepha.location.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.location.dto.request.ReverseGeocodeRequest;
import com.ektrepha.location.dto.response.AutocompleteResponse;
import com.ektrepha.location.dto.response.PlaceDetailsResponse;
import com.ektrepha.location.dto.response.ReverseGeocodeResponse;
import com.ektrepha.location.service.LocationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Ektrepha's own location endpoints — not Ola Maps' own paths — backing the Location/Confirm Location screens. See {@link com.ektrepha.location.impl.OlaMapsClientImpl} for the actual provider integration. */
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

	private final LocationService locationService;

	@PostMapping("/reverse-geocode")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<ReverseGeocodeResponse> reverseGeocode(Authentication authentication, @Valid @RequestBody ReverseGeocodeRequest request) {
		return ResponseEntity.ok(locationService.reverseGeocode(userId(authentication), request));
	}

	@GetMapping("/autocomplete")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<AutocompleteResponse> autocomplete(Authentication authentication, @RequestParam String query,
			@RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
		return ResponseEntity.ok(locationService.autocomplete(userId(authentication), query, lat, lng));
	}

	@GetMapping("/place-details")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<PlaceDetailsResponse> placeDetails(Authentication authentication, @RequestParam String placeId) {
		return ResponseEntity.ok(locationService.placeDetails(userId(authentication), placeId));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
