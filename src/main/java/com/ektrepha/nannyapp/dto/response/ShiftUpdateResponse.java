package com.ektrepha.nannyapp.dto.response;

import java.time.Instant;

public record ShiftUpdateResponse(
		String id, Instant at, String text) {
}
