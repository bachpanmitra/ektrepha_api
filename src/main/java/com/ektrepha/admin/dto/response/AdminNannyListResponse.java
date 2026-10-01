package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminNannyListResponse(
		List<AdminNannySummaryResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages) {
}
