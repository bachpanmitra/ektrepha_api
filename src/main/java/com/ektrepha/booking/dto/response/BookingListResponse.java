package com.ektrepha.booking.dto.response;

import java.util.List;

public record BookingListResponse(
		List<BookingCardResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages) {
}
