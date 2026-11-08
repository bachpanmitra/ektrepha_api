package com.ektrepha.verification.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/** A nanny's own view of one of her verification documents — "My documents" screen. */
public record MyVerificationDocumentResponse(
		Long id,
		String type,
		String status,
		String rejectionReason,
		Instant createdAt,
		Instant reviewedAt,
		LocalDate expiryDate) {
}
