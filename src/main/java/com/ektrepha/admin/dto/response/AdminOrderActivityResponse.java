package com.ektrepha.admin.dto.response;

import java.time.Instant;
import java.util.Map;

/** One row of a booking's activity timeline - see {@code order_activity} (migration 039). */
public record AdminOrderActivityResponse(
		String eventType,
		Instant occurredAt,
		String actorType,
		String actorName,
		Map<String, Object> metadata) {
}
