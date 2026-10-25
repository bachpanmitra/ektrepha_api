package com.ektrepha.payment.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.payment.dto.response.PaymentHistoryListResponse;
import com.ektrepha.payment.service.PaymentHistoryService;

import lombok.RequiredArgsConstructor;

/** Payments screen (ektrepha_ui's L6) — read-only, across both childcare and elder-care bookings. */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentHistoryController {

	private final PaymentHistoryService paymentHistoryService;

	@GetMapping("/history")
	@PreAuthorize("hasRole('PARENT')")
	public ResponseEntity<PaymentHistoryListResponse> history(Authentication authentication,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		Long userId = Long.valueOf(authentication.getName());
		return ResponseEntity.ok(paymentHistoryService.list(userId, page, pageSize));
	}

}
