package com.ektrepha.nanny.dto.response;

import java.time.Instant;

/** Never carries {@code s3Key}/{@code vendorReferenceId}/{@code reviewedBy}/{@code rejectionReason} — S2 shows that verification happened, never the underlying document or PII. */
public record VerificationItem(
		String type,
		String label,
		String description,
		String status,
		Instant verifiedAt) {
}
