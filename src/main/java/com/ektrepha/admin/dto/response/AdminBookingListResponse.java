package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminBookingListResponse(
		List<AdminBookingSummaryResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages) {
}
