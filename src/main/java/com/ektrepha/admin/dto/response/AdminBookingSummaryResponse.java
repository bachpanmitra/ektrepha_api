package com.ektrepha.admin.dto.response;

import java.time.Instant;

/**
 * One row of the admin "Bookings" list. {@code childCount} is 0 or 1, not a real count — the
 * {@code booking} table links at most one {@code child_id} per row (see {@code Booking#child}); it
 * is not a per-family child count.
 */
public record AdminBookingSummaryResponse(
		Long id,
		String serviceTypeCode,
		String serviceTypeName,
		String parentName,
		String city,
		int childCount,
		Long nannyId,
		String nannyName,
		Instant startTime,
		Instant endTime,
		String status) {
}
