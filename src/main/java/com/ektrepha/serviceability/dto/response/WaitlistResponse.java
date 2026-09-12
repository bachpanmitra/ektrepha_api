package com.ektrepha.serviceability.dto.response;

import java.time.Instant;

public record WaitlistResponse(
		Long id,
		String pincode,
		String serviceTypeCode,
		String contact,
		Instant createdAt) {
}
