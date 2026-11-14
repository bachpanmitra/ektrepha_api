package com.ektrepha.caremanager.controller;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.dto.request.DutyShiftRequest;
import com.ektrepha.caremanager.dto.response.DutyShiftListResponse;
import com.ektrepha.caremanager.dto.response.DutyShiftResponse;
import com.ektrepha.caremanager.service.AdminDutyShiftService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "Admin" — role ADMIN only (SecurityConfig: /v1/admin/**). */
@RestController
@RequestMapping("/v1/admin/duty-shifts")
@RequiredArgsConstructor
public class AdminDutyShiftController {

	private final AdminDutyShiftService adminDutyShiftService;

	@GetMapping
	public DutyShiftListResponse list(@RequestParam Instant from, @RequestParam Instant to) {
		return adminDutyShiftService.list(from, to);
	}

	@PostMapping
	public ResponseEntity<DutyShiftResponse> create(@Valid @RequestBody DutyShiftRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminDutyShiftService.create(request));
	}

}
