package com.ektrepha.verification.dto.response;

import java.time.Instant;

public record NannyReferenceResponse(
		Long id,
		Long nannyId,
		String name,
		String phone,
		String relationship,
		String status,
		Instant createdAt,
		Instant verifiedAt,
		String rejectionReason) {
}
