package com.ektrepha.caremanager.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.dto.response.AccessLogPageResponse;
import com.ektrepha.caremanager.dto.response.ChangeRequestPageResponse;
import com.ektrepha.caremanager.service.AdminAccessLogService;
import com.ektrepha.caremanager.service.AdminCmChangeRequestService;
import com.ektrepha.model.AccessLogRecordType;

import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "Admin" — role ADMIN only (SecurityConfig: /v1/admin/**). */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminCmOpsController {

	private final AdminCmChangeRequestService adminCmChangeRequestService;
	private final AdminAccessLogService adminAccessLogService;

	@GetMapping("/cm-change-requests")
	public ChangeRequestPageResponse listChangeRequests(@RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer limit) {
		return adminCmChangeRequestService.list(cursor, limit);
	}

	@GetMapping("/access-log")
	public AccessLogPageResponse searchAccessLog(
			@RequestParam(required = false) String actor,
			@RequestParam(name = "record_type", required = false) AccessLogRecordType recordType,
			@RequestParam(name = "record_id", required = false) String recordId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer limit) {
		return adminAccessLogService.search(actor, recordType, recordId, from, to, cursor, limit);
	}

}
