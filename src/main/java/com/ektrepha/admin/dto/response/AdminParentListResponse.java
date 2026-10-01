package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminParentListResponse(
		List<AdminParentSummaryResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages) {
}
