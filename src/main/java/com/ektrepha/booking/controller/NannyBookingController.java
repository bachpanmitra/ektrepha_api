package com.ektrepha.booking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;
import com.ektrepha.booking.dto.response.NannyTodayBookingResponse;
import com.ektrepha.booking.service.NannyBookingService;
import com.ektrepha.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** NANNY-role actions on hourly-care bookings: the assigned caregiver's own "Live care" actions - Start/Complete. Caregiver assignment itself lives at the ADMIN-only {@code POST /api/v1/admin/hourly-care/bookings/{id}/assign} (see {@link com.ektrepha.hourlycare.controller.HourlyCareAdminController}); it is not exposed here. Distinct base path from /api/v1/bookings, which is entirely PARENT-scoped. */
@Tag(name = "Nanny Bookings", description = "NANNY-role actions on hourly-care bookings: Live Care start/complete.")
@RestController
@RequestMapping("/api/v1/nanny-bookings")
@RequiredArgsConstructor
public class NannyBookingController {

	private final NannyBookingService nannyBookingService;

	@Operation(summary = "Today's shift", description = "The assigned caregiver's own booking for today, or 200 with an empty body if nothing is assigned.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Today's booking, or nothing assigned"),
			@ApiResponse(responseCode = "401", description = "Missing/invalid bearer token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "Caller is not NANNY-role", content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
	@GetMapping("/today")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<NannyTodayBookingResponse> today(Authentication authentication) {
		return ResponseEntity.ok(nannyBookingService.today(userId(authentication)));
	}

	@Operation(summary = "Booking detail", description = "One of the assigned caregiver's own bookings, by id.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Booking detail"),
			@ApiResponse(responseCode = "401", description = "Missing/invalid bearer token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "Caller is not NANNY-role", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "No booking with the given id assigned to this caregiver", content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
	@GetMapping("/{id}")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<NannyTodayBookingResponse> detail(Authentication authentication,
			@Parameter(description = "Booking id") @PathVariable Long id) {
		return ResponseEntity.ok(nannyBookingService.detail(userId(authentication), id));
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
