package com.ektrepha.nannysearch.dto.response;

import java.util.List;

/** Hand-rolled paging, not Spring's {@code Page<T>} — ranking needs the whole candidate set for min/max normalization, so paging happens after ranking, in the service layer. */
public record NannySearchResponse(
		List<NannySearchResultItem> results,
		int page,
		int pageSize,
		int totalResults) {
}
