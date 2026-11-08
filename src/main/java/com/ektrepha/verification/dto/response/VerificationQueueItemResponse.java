package com.ektrepha.verification.dto.response;

import java.time.Instant;

/** One row in the ADMIN cross-nanny document review queue (API Map row 20) — unlike {@code MyVerificationDocumentResponse}/{@code VerificationDocumentResponse}, this spans every nanny, so it carries whose document this is. */
public record VerificationQueueItemResponse(
		Long id,
		Long nannyId,
		String nannyName,
		String type,
		String status,
		Instant createdAt) {
}
