package com.ektrepha.caremanager.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.CmCallerResolver;
import com.ektrepha.caremanager.dto.request.FlagReviewRequest;
import com.ektrepha.caremanager.dto.response.NannyDetailResponse;
import com.ektrepha.caremanager.dto.response.NannyListItemResponse;
import com.ektrepha.caremanager.dto.response.NannyListPageResponse;
import com.ektrepha.caremanager.service.CmNannyService;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.NannyOperationalStatus;
import com.ektrepha.model.User;
import com.ektrepha.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "CM - nannies" */
@RestController
@RequestMapping("/v1/cm")
@RequiredArgsConstructor
public class CmNannyController {

	private final CmCallerResolver cmCallerResolver;
	private final CmNannyService cmNannyService;
	private final UserRepository userRepository;

	@GetMapping("/me/nannies")
	public NannyListPageResponse listMyNannies(
			@RequestParam(required = false) NannyOperationalStatus status,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer limit,
			Authentication authentication) {
		return cmNannyService.listMyNannies(cmCallerResolver.resolveOwnCareManager(authentication), status, cursor, limit);
	}

	@GetMapping("/nannies")
	public NannyListPageResponse searchNannies(@RequestParam String q,
			@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
		return cmNannyService.searchNannies(q, cursor, limit);
	}

	@GetMapping("/nannies/{nannyId}")
	public NannyDetailResponse getNannyDetail(@PathVariable String nannyId, Authentication authentication) {
		return cmNannyService.getNannyDetail(cmCallerResolver.resolve(authentication), actor(authentication), nannyId);
	}

	@PostMapping("/nannies/{nannyId}/flag-review")
	public NannyListItemResponse reviewFlag(@PathVariable String nannyId, @Valid @RequestBody FlagReviewRequest request,
			Authentication authentication) {
		return cmNannyService.reviewFlag(cmCallerResolver.resolve(authentication), actor(authentication), nannyId, request);
	}

	private User actor(Authentication authentication) {
		Long userId = Long.valueOf(authentication.getName());
		return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
	}

}
