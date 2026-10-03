package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminNannyStatusHistoryResponse(
		Long id,
		String previousStatus,
		String newStatus,
		String reason,
		String changedByName,
		Instant changedAt) {
}
