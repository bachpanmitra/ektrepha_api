package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminOrderActivityFeedResponse(
		List<AdminOrderActivityFeedItemResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages) {
}
