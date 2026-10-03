package com.ektrepha.verification.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.model.NannyInterview;
import com.ektrepha.verification.dto.request.NannyInterviewOutcomeRequest;
import com.ektrepha.verification.dto.request.NannyInterviewScheduleRequest;
import com.ektrepha.verification.dto.response.NannyInterviewResponse;
import com.ektrepha.verification.service.NannyInterviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** PRD: "Video interview with Ektrepha staff, outcome recorded." Both scheduling and recording the outcome are ADMIN (staff) actions. */
@Tag(name = "Nanny Interviews", description = "Scheduling and outcome recording for the mandatory video interview.")
@RestController
@RequestMapping("/api/v1/nanny-verification/interviews")
@RequiredArgsConstructor
public class NannyInterviewController {

	private final NannyInterviewService nannyInterviewService;

	@Operation(summary = "Schedule an interview", description = "Lands as SCHEDULED - no rollup effect until an outcome is recorded.")
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<NannyInterviewResponse> schedule(@Valid @RequestBody NannyInterviewScheduleRequest request) {
		NannyInterview interview = nannyInterviewService.schedule(request.nannyId(), request.scheduledAt());
		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(interview));
	}

	@Operation(summary = "List a nanny's interviews", description = "Newest first.")
	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<NannyInterviewResponse>> list(@RequestParam Long nannyId) {
		return ResponseEntity.ok(nannyInterviewService.list(nannyId).stream().map(this::toResponse).toList());
	}

	@Operation(summary = "Record an interview outcome", description = "PASSED/FAILED/NEEDS_FOLLOWUP, and recomputes the owning nanny's rollup - FAILED forces REJECTED.")
	@PostMapping("/{id}/outcome")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<NannyInterviewResponse> recordOutcome(Authentication authentication,
			@Parameter(description = "nanny_interview row id") @PathVariable Long id, @Valid @RequestBody NannyInterviewOutcomeRequest request) {
		NannyInterview interview = nannyInterviewService.recordOutcome(id, request.outcome(), userId(authentication), request.notes());
		return ResponseEntity.ok(toResponse(interview));
	}

	private NannyInterviewResponse toResponse(NannyInterview interview) {
		return new NannyInterviewResponse(interview.getId(), interview.getNanny().getId(), interview.getScheduledAt(),
				interview.getOutcome().name(), interview.getConductedAt(), interview.getNotes());
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
