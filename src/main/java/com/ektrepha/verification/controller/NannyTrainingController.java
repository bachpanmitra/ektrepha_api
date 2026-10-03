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
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.verification.dto.request.TrainingAttemptSubmitRequest;
import com.ektrepha.verification.dto.response.QuizQuestionResponse;
import com.ektrepha.verification.dto.response.TrainingAttemptResponse;
import com.ektrepha.verification.service.NannyTrainingService;
import com.ektrepha.verification.training.ChildSafetyTrainingQuiz;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** PRD: "Mandatory child safety training module + quiz (code of conduct, safe touch boundaries, emergencies, reporting duties, photo/privacy rules). Must pass." */
@Tag(name = "Nanny Training", description = "The mandatory child-safety training module's quiz.")
@RestController
@RequestMapping("/api/v1/nanny-verification/training")
@RequiredArgsConstructor
public class NannyTrainingController {

	private final NannyTrainingService nannyTrainingService;

	@Operation(summary = "Get the current quiz", description = "Never includes the correct answer for any question.")
	@GetMapping("/quiz")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<List<QuizQuestionResponse>> quiz() {
		return ResponseEntity.ok(ChildSafetyTrainingQuiz.QUESTIONS.stream()
				.map(q -> new QuizQuestionResponse(q.id(), q.topic(), q.prompt(), q.options()))
				.toList());
	}

	@Operation(summary = "Submit a quiz attempt", description = "Graded server-side; persists the attempt and recomputes the owning nanny's rollup.")
	@PostMapping("/attempts")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<TrainingAttemptResponse> submit(Authentication authentication, @Valid @RequestBody TrainingAttemptSubmitRequest request) {
		var attempt = nannyTrainingService.submit(userId(authentication), request.startedAt(), request.answers());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new TrainingAttemptResponse(attempt.getId(), attempt.getModuleVersion(), attempt.getScore(), attempt.isPassed(), attempt.getCompletedAt()));
	}

	@Operation(summary = "List a nanny's training attempts", description = "Admin-only, newest first.")
	@GetMapping("/{nannyId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<TrainingAttemptResponse>> history(@PathVariable Long nannyId) {
		return ResponseEntity.ok(nannyTrainingService.history(nannyId).stream()
				.map(a -> new TrainingAttemptResponse(a.getId(), a.getModuleVersion(), a.getScore(), a.isPassed(), a.getCompletedAt()))
				.toList());
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
