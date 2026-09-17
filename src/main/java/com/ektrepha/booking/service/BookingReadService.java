package com.ektrepha.booking.service;

import com.ektrepha.booking.dto.response.BookingDetailResponse;
import com.ektrepha.booking.dto.response.BookingListResponse;

public interface BookingReadService {

	/** {@code scope} is "active" (PENDING/CONFIRMED/IN_PROGRESS, ascending) or "history" (COMPLETED/CANCELLED, descending) — see PRD "one query, one status-set param". */
	BookingListResponse list(Long userId, String scope, int page, int pageSize);

	BookingDetailResponse get(Long userId, Long bookingId);

}
