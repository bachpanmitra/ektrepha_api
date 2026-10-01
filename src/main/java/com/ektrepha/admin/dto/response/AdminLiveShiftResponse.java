package com.ektrepha.admin.dto.response;

import java.time.Instant;

public record AdminLiveShiftResponse(
		Long bookingId,
		Long nannyId,
		String nannyName,
		String parentName,
		String city,
		Instant startTime,
		Instant endTime) {
}
