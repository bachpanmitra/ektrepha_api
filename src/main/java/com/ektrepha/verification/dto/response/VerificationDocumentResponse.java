package com.ektrepha.verification.dto.response;

import java.time.Instant;

/** Never carries {@code s3Key} - same rule as {@link com.ektrepha.nanny.dto.response.VerificationItem}, just for the record itself rather than the S2 rollup view. */
public record VerificationDocumentResponse(
		Long id,
		Long nannyId,
		String type,
		String status,
		Instant createdAt,
		Instant reviewedAt) {
}
