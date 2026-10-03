package com.ektrepha.admin.dto.response;

import java.time.Instant;
import java.util.Map;

/** One row of the cross-booking admin activity feed - same shape as {@link AdminOrderActivityResponse}
 * plus enough booking context ({@code bookingId}/{@code serviceTypeName}/{@code parentName}) to be
 * useful outside a single booking's own page. */
public record AdminOrderActivityFeedItemResponse(
		Long bookingId,
		String serviceTypeName,
		String parentName,
		String eventType,
		Instant occurredAt,
		String actorType,
		String actorName,
		Map<String, Object> metadata) {
}
