package com.ektrepha.caremanager.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.CmCallerResolver;
import com.ektrepha.caremanager.dto.response.CmSummaryResponse;
import com.ektrepha.caremanager.service.CmSummaryService;

import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "CM - summary and tasks" — /me/ is Care-Manager-only (an Admin has no "mine"). */
@RestController
@RequestMapping("/v1/cm/me")
@RequiredArgsConstructor
public class CmSummaryController {

	private final CmCallerResolver cmCallerResolver;
	private final CmSummaryService cmSummaryService;

	@GetMapping("/summary")
	public CmSummaryResponse summary(Authentication authentication) {
		return cmSummaryService.summarize(cmCallerResolver.resolveOwnCareManager(authentication));
	}

}
