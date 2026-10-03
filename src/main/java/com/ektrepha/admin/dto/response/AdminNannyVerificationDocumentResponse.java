package com.ektrepha.admin.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record AdminNannyVerificationDocumentResponse(
		Long id,
		String type,
		String status,
		Instant createdAt,
		Instant reviewedAt,
		String rejectionReason,
		/** Short-lived presigned GET URL for the uploaded file (null when S3 is disabled, e.g. local dev). */
		String documentUrl,
		/** Only ever set on a BACKGROUND_CHECK (PCC) row. */
		LocalDate expiryDate) {
}
