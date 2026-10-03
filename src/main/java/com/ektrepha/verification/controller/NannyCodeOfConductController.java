package com.ektrepha.verification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyCodeOfConductAcceptance;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.verification.CodeOfConductDocument;
import com.ektrepha.verification.dto.response.CodeOfConductAcceptanceResponse;
import com.ektrepha.verification.dto.response.CodeOfConductResponse;
import com.ektrepha.verification.service.NannyCodeOfConductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** PRD: "Signed code of conduct (stored with timestamp and version)." */
@Tag(name = "Nanny Code of Conduct", description = "The caregiver code of conduct and its acceptance record.")
@RestController
@RequestMapping("/api/v1/nanny-verification/code-of-conduct")
@RequiredArgsConstructor
public class NannyCodeOfConductController {

	private final NannyCodeOfConductService nannyCodeOfConductService;
	private final NannyRepository nannyRepository;

	@Operation(summary = "Get the current code of conduct", description = "Also reports whether the caller has already accepted this version.")
	@GetMapping
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<CodeOfConductResponse> get(Authentication authentication) {
		Long nannyId = nannyRepository.findByUserId(userId(authentication)).map(Nanny::getId).orElse(null);
		boolean accepted = nannyId != null && nannyCodeOfConductService.hasAcceptedCurrentVersion(nannyId);
		return ResponseEntity.ok(new CodeOfConductResponse(CodeOfConductDocument.CURRENT_VERSION, CodeOfConductDocument.TEXT, accepted));
	}

	@Operation(summary = "Accept the current code of conduct", description = "Records acceptance with a timestamp and version, then recomputes the caller's nanny rollup.")
	@PostMapping("/accept")
	@PreAuthorize("hasRole('NANNY')")
	public ResponseEntity<CodeOfConductAcceptanceResponse> accept(Authentication authentication, HttpServletRequest httpRequest) {
		NannyCodeOfConductAcceptance acceptance = nannyCodeOfConductService.accept(userId(authentication), httpRequest.getRemoteAddr());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new CodeOfConductAcceptanceResponse(acceptance.getId(), acceptance.getVersion(), acceptance.getAcceptedAt()));
	}

	@Operation(summary = "List a nanny's code-of-conduct acceptances", description = "Admin-only, newest first.")
	@GetMapping("/{nannyId}/history")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<CodeOfConductAcceptanceResponse>> history(@PathVariable Long nannyId) {
		return ResponseEntity.ok(nannyCodeOfConductService.history(nannyId).stream()
				.map(a -> new CodeOfConductAcceptanceResponse(a.getId(), a.getVersion(), a.getAcceptedAt()))
				.toList());
	}

	private Long userId(Authentication authentication) {
		return Long.valueOf(authentication.getName());
	}

}
