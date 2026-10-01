package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminNannyReviewListResponse(
		List<AdminNannyReviewResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages) {
}
