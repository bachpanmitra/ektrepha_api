package com.ektrepha.nanny.dto.response;

import java.util.List;

public record VerificationSummaryResponse(
		Long nannyId,
		String firstName,
		String lastName,
		String photoUrl,
		String overallStatus,
		int completedCount,
		int totalCount,
		List<VerificationItem> items) {
}
