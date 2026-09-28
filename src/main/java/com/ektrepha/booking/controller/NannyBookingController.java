package com.ektrepha.booking.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;
import com.ektrepha.booking.service.NannyBookingService;
import com.ektrepha.exception.ErrorResponse;
import com.ektrepha.hourlycare.dto.request.AssignCaregiverRequest;
import com.ektrepha.hourlycare.dto.response.CaregiverAssignmentResponse;
import com.ektrepha.hourlycare.service.HourlyCareService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** NANNY-role actions on hourly-care bookings: picking the caregiver for a booking ({@code /assign} - public, no auth required per SecurityConfig, same call as {@link com.ektrepha.hourlycare.controller.HourlyCareAdminController}'s ADMIN-only equivalent), and the assigned caregiver's own "Live care" actions - Start/Complete (still NANNY-only). Distinct base path from /api/v1/bookings, which is entirely PARENT-scoped. */
@Tag(name = "Nanny Bookings", description = "NANNY-role actions on hourly-care bookings: caregiver self-assignment and Live Care start/complete.")
@RestController
@RequestMapping("/api/v1/nanny-bookings")
@RequiredArgsConstructor
public class NannyBookingController {

	private final NannyBookingService nannyBookingService;
	private final HourlyCareService hourlyCareService;

	@Operation(summary = "Assign caregiver to a booking",
			description = "Picks the in-house caregiver for a booking once it has moved to ASSIGNING_CAREGIVER (i.e. payment is confirmed). "
					+ "On success the booking moves to CONFIRMED. Same underlying call as the ADMIN-only "
					+ "POST /api/v1/admin/hourly-care/bookings/{id}/assign. "
					+ "Public: no bearer token required (see SecurityConfig).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Caregiver assigned; booking is now CONFIRMED",
					content = @Content(schema = @Schema(implementation = CaregiverAssignmentResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation failed (e.g. missing nannyId)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "No booking or nanny with the given id",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Booking isn't awaiting assignment, or the chosen caregiver "
					+ "doesn't serve this zone/service or already has a conflicting booking",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping(value = "/{id}/assign", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<CaregiverAssignmentResponse> assign(
			@Parameter(description = "Booking id", example = "123") @PathVariable Long id,
			@Valid @RequestBody AssignCaregiverRequest request) {
		return ResponseEntity.ok(hourlyCareService.assignCaregiver(id, request));
	}

	@Operation(summary = "Start live care", description = "Marks the assigned caregiver's shift as started.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Care started"),
			@ApiResponse(responseCode = "401", description = "Missing/invalid bearer token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "Caller is not NANNY-role", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "No booking with the given id", content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping("/{id}/start")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<BookingLifecycleResponse> start(Authentication authentication,
			@Parameter(description = "Booking id") @PathVariable Long id) {
		return ResponseEntity.ok(nannyBookingService.startCare(userId(authentication), id));
	}

	@Operation(summary = "Complete live care", description = "Marks the assigned caregiver's shift as completed.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Care completed"),
			@ApiResponse(responseCode = "401", description = "Missing/invalid bearer token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "Caller is not NANNY-role", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "No booking with the given id", content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
	@PostMapping("/{id}/complete")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<BookingLifecycleResponse> complete(Authentication authentication,
			@Parameter(description = "Booking id") @PathVariable Long id) {
		return ResponseEntity.ok(nannyBookingService.completeCare(userId(authentication), id));
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
