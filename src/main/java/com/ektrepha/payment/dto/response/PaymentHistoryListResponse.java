package com.ektrepha.payment.dto.response;

import java.util.List;

public record PaymentHistoryListResponse(
		List<PaymentHistoryItemResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages) {
}
