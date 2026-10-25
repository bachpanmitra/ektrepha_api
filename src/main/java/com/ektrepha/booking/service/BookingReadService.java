package com.ektrepha.booking.service;

import com.ektrepha.booking.dto.response.BookingDetailResponse;
import com.ektrepha.booking.dto.response.BookingListResponse;

public interface BookingReadService {

	/**
	 * {@code scope} is "active" (PENDING/CONFIRMED/IN_PROGRESS plus the hourly-care pay-first flow's
	 * pre-confirmation AWAITING_PAYMENT/ASSIGNING_CAREGIVER, ascending), "history" (COMPLETED/
	 * CANCELLED, descending), or "all" (every status, descending) — see PRD "one query, one
	 * status-set param". {@code serviceTypeCode} is an optional filter (e.g. "childcare",
	 * "senior_care") - null/omitted returns every vertical, unchanged from before this param existed.
	 */
	BookingListResponse list(Long userId, String scope, int page, int pageSize, String serviceTypeCode);

	BookingDetailResponse get(Long userId, Long bookingId);

}
