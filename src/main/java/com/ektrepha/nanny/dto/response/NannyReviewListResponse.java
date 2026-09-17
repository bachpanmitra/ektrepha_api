package com.ektrepha.nanny.dto.response;

import java.util.List;

public record NannyReviewListResponse(
		List<PublicReviewResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages,
		Double ratingAvg,
		int reviewCount) {
}
