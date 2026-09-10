package com.ektrepha.nannysearch.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.nannysearch.dto.response.LanguageOptionResponse;
import com.ektrepha.nannysearch.dto.response.NannySearchResponse;
import com.ektrepha.nannysearch.dto.response.SkillOptionResponse;
import com.ektrepha.nannysearch.service.NannySearchService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/nanny-search")
@RequiredArgsConstructor
public class NannySearchController {

	private final NannySearchService nannySearchService;

	// Runs a filtered, geo-bounded, ranked nanny search for the authenticated parent. POST (not
	// GET) because the payload carries lists (languages/skills) and a time-window object.
	@PostMapping
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<NannySearchResponse> search(Authentication authentication, @Valid @RequestBody NannySearchRequest request) {
		Long userId = Long.valueOf(authentication.getName());
		return ResponseEntity.ok(nannySearchService.search(userId, request));
	}

	// Lists active languages for populating the search form's language filter.
	@GetMapping("/languages")
	public ResponseEntity<List<LanguageOptionResponse>> listLanguages() {
		return ResponseEntity.ok(nannySearchService.listActiveLanguages());
	}

	// Lists the skill catalog for populating the search form's skills filter.
	@GetMapping("/skills")
	public ResponseEntity<List<SkillOptionResponse>> listSkills() {
		return ResponseEntity.ok(nannySearchService.listSkills());
	}

}
